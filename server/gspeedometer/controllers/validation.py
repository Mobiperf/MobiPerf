# Copyright (c) 2012, University of Washington
# All rights reserved.
#
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

""" Runs data validation tests and prints results to e-mail or web page."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging

import datetime, time

from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from google.appengine.ext import db
from google.appengine.api import taskqueue

from gspeedometer import model
from gspeedometer import config
from gspeedometer.helpers import util
from gspeedometer.measurement import dns, http, ping, traceroute
from google.appengine.api import mail
from gspeedometer.controllers import measurement

class Validation(webapp.RequestHandler):
  """Controller for the validation view."""

  PRINT_INVALID = True  # if true, print details of erroneous data
  TESTING = False  # if true, print testing data
  PRINT_VALUES = False  # if 
  USE_WEBPAGE = True  # if true, renders for webpage; otherwise, sends email

  validation_results = dict()  # stores the results to render
  type_to_summary = dict()  # map of measurement type to summary object
  type_to_details = dict()  # map of measurement type to details object

  def Validate(self, **unused_args):
    """Main handler for the validation view.
    Note that this method is called from a request that is restricted to 
    admin privileges. 
      
    Args (HTTP request parameters):
      start_time: start time for validation period (ISO8601 format)
      end_time: end time for validation period (ISO8601 format)
      iters: (optional) Number of days to do bulk validation on. (int)
      use_webpage: (optional) Show validation results in HTML. If set to false, 
          send an e-mail with results. (boolean)
      worker: (optional) True if running as a queued task. (boolean)
  
    Returns:
      Result of validation, or nothing.
  
    Raises:
       No exceptions handled here.
       No new exceptions generated here.      
    
    """

    iters = self.request.get('iters')
    start_time = self.request.get('start_time')
    end_time = self.request.get('end_time')

    # if iterating, set up and enqueue validation tasks
    while iters and int(iters) > 1:
      start_time = 24 * 60 * 60 * 1000 * 1000 + \
          util.TimeToMicrosecondsSinceEpoch(util.StringToTime(start_time))
      start_time = util.MicrosecondsSinceEpochToTime(start_time)
      start_time = util.TimeToString(start_time)
      end_time = 24 * 60 * 60 * 1000 * 1000 + \
          util.TimeToMicrosecondsSinceEpoch(util.StringToTime(end_time))

      end_time = util.MicrosecondsSinceEpochToTime(end_time)
      end_time = util.TimeToString(end_time)
      # Add the task to the 'validation' queue.
      taskqueue.add(
          url='/validation/data?worker=true&start_time=%s&end_time=%s' %
          (start_time, end_time), method='GET', queue_name='validation')
      iters = int(iters) - 1

    # return here if iterating using task queue
    if iters:
      self.response.out.write("{Success:true}")
      return

    # contains validation results for printing
    self.validation_results = dict()

    # support only the measurements specified in MEASUREMENT_TYPES
    for mtype, name in measurement.MEASUREMENT_TYPES:
      self.type_to_summary[mtype] = \
         model.ValidationSummary(measurement_type=mtype)
      self.type_to_details[mtype] = []

    # validate all the data in one pass  
    self._DoValidation(self.request.get('start_time'),
                       self.request.get('end_time'), self.request.get('limit'))

    # validation results are in type_to_details, now write them to datastore
    for mtype, data in self.type_to_summary.items():
      data.put()  # must put summary before putting details that reference them
      if self.type_to_details.has_key(mtype):
        for detail in self.type_to_details[mtype]:
          detail.summary = data
          detail.put()

    # if this was a queued task, return here
    if self.request.get('worker'):
      self.response.out.write("{Success:true}")
      return

    # for display purposes, render HTML of results
    html = template.render(
        'templates/validation.html', self.validation_results)

    # send to response, or e-mail user
    if self.request.get('use_webpage'):
      self.response.out.write(html)
    else:
      message = mail.EmailMessage(
        sender=config.VALIDATION_EMAIL_SENDER,
        subject="Daily validation results")
      message.to = config.VALIDATION_EMAIL_RECIPIENT
      message.body = html
      message.html = html
      message.send()

  def _DoValidation(self, start_time, end_time, limit):
    """Gets all the records for a specified type, subject
    to request parameters. 
      
    Args:
      None. This reads from the measurement request
  
    Returns:
      Nothing. It writes results to an instance variable.
  
    Raises:
       No exceptions handled here.
       No new exceptions generated here.      
    
    """
    # manually specified parameters for testing
    if self.TESTING:
      limit = 100
      start_time = util.TimeToMicrosecondsSinceEpoch(
          util.StringToTime("2012-03-20T00:00:00Z"))
      end_time = util.TimeToMicrosecondsSinceEpoch(
          util.StringToTime("2012-03-21T00:00:00Z"))

    # set up query filters according to parameters
    query = model.Measurement.all()

    if start_time:
      dt_start = util.TimeToMicrosecondsSinceEpoch(
          util.StringToTime(start_time))
    else:
      dt_start = util.TimeToMicrosecondsSinceEpoch(
          datetime.datetime.utcfromtimestamp(time.time()) -
          datetime.timedelta(days=1))
    self.validation_results['start_time'] = \
       util.MicrosecondsSinceEpochToTime(dt_start)
    query.filter('timestamp >=', dt_start)

    if end_time:
      dt = util.TimeToMicrosecondsSinceEpoch(util.StringToTime(end_time))
    else:
      dt = util.TimeToMicrosecondsSinceEpoch(
          datetime.datetime.utcfromtimestamp(time.time()))
    self.validation_results['end_time'] = util.MicrosecondsSinceEpochToTime(dt)

    query.filter('timestamp <', dt)
    query.order('timestamp')
    if limit:
      results = query.fetch(int(limit))
    else:
      results = query

    error_type_to_count = dict()  # map of error type to count  
    num_invalid = dict()  # map of measurement type to count of invalid

    for measurement in results:
      # Need to catch case where device has been deleted
      try:
        unused_device_info = measurement.device_properties.device_info
      except db.ReferencePropertyResolveError:
        logging.exception('Device deleted for measurement %s',
                          measurement.key().id())
        # Skip this measurement
        continue

      # catch case where measurement is not supported
      if not self.type_to_summary.has_key(measurement.type):
        self.type_to_summary[measurement.type] = \
         model.ValidationSummary(measurement_type=measurement.type,
                                 error="UnknownType")

      # set initial data for validation summary
      if not self.type_to_summary[measurement.type].timestamp_start:
        self.type_to_summary[measurement.type].timestamp_start = \
            util.MicrosecondsSinceEpochToTime(dt_start)
        self.type_to_summary[measurement.type].timestamp_end = \
            util.MicrosecondsSinceEpochToTime(dt)
        self.type_to_summary[measurement.type].record_count = 1
        self.type_to_summary[measurement.type].error_count = 0
        num_invalid[measurement.type] = 0
      else: self.type_to_summary[measurement.type].record_count += 1

      # Make a measurement-specific validation object
      try:
        validator = MeasurementValidatorFactory.CreateValidator(measurement)
      except RuntimeError:
        continue  # no validator defined, continue

      valid = validator.Validate()
      if not valid['valid']:
        if not num_invalid.has_key(measurement.type):
          num_invalid[measurement.type] = 0
        num_invalid[measurement.type] += 1

      # print the error details
      if self.PRINT_VALUES or (self.PRINT_INVALID and not valid['valid']):
        if not self.validation_results.has_key('%s_invalid_detail'
                                               % measurement.type):
          self.validation_results['%s_invalid_detail' % measurement.type] = []
        self.validation_results['%s_invalid_detail' % measurement.type].append(
              'Errors: %s <br>\nTime: %s<br>\nDevice: %s<br>\nDetails: %s' %
              (", ".join(valid['error_types']), str(measurement.timestamp),
               measurement.device_properties.device_info.id,
               validator.GetHTML()))

        # update error counters
        for error in valid['error_types']:
          if not error_type_to_count.has_key(measurement.type):
              error_type_to_count[measurement.type] = dict()
          if not error_type_to_count[measurement.type].has_key(error):
            error_type_to_count[measurement.type][error] = 1
          else: error_type_to_count[measurement.type][error] += 1

        # create ValidationEntry for each error
        if len(valid['error_types']) > 0:
          error_detail = model.ValidationEntry()
          error_detail.measurement = measurement
          error_detail.error_types = valid['error_types']
          self.type_to_details[measurement.type].append(error_detail)

      # update number of invalid measurements
      self.validation_results['%s_invalid' % measurement.type] = \
        num_invalid[measurement.type]

    # update validation summary entity for each data type
    for data_type in self.type_to_summary.keys():
      if num_invalid.has_key(data_type):
        self.type_to_summary[data_type].error_count = num_invalid[data_type]
      if error_type_to_count.has_key(data_type):
        self.type_to_summary[data_type].SetErrorByType(
            error_type_to_count[data_type])

      self.validation_results[data_type + "_count"] = \
        self.type_to_summary[data_type].record_count

class MeasurementValidatorFactory:
  """Class with a single static method for creating measurement-specific 
  validation objects."""

  @staticmethod
  def CreateValidator(measurement):
    """Gets all the records for a specified type, subject
    to request parameters. 
      
    Args:
      measurement: A Measurement entity.
  
    Returns:
      A validation object corresponding to the measurement.
  
    Raises:
       RuntimeError if measurement type isn't supported.      
    
    """
    if measurement.type == 'ping':
      return ping.Ping(measurement.Params(), measurement.Values())
    elif measurement.type == 'dns_lookup':
      return dns.DNSLookup(measurement.Params(), measurement.Values())
    elif measurement.type == 'traceroute':
      return traceroute.Traceroute(measurement.Params(), measurement.Values())
    elif measurement.type == 'http':
      return http.HTTP(measurement.Params(), measurement.Values())
    else:
      raise RuntimeError("Unknown measurement type %s" % measurement.type)

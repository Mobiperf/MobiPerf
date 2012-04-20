# Copyright 2012 University of Washington.
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
#

""" Runs data validation tests and prints results to e-mail or web page."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging

import datetime, time

from django.utils import simplejson as json
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from google.appengine.ext import db

from gspeedometer import config
from gspeedometer import model
from gspeedometer.helpers import util
from gspeedometer.data import traceroute
from gspeedometer.data import ping
from gspeedometer.data import dns, http
from gspeedometer.helpers import acl
from google.appengine.api import mail

class Validation(webapp.RequestHandler):
  """Controller for the validation view."""

  PRINT_INVALID = True # if true, print details of erroneous data
  TESTING = False # if true, pint testing data
  PRINT_VALUES = False # if 
  USE_WEBPAGE = True # if true, renders for webpage; otherwise, sends email

  validation_results = dict() # stores the results to render

  def Validate(self, **unused_args):
    """Main handler for the validation view."""
    
    # should never be reached due to app.yaml 
    # configuration, but we're being safe
    if not acl.UserIsScheduleAdmin():
      self.error(404)
      return
        
    # contains validation results for printing
    self.validation_results = dict()
    
    # TODO(drc) validate all the data in one pass, 
    # regardless of the type
    self._ValidateTraceroutes()    
    self._ValidatePings()    
    self._ValidateDnsLookup()
    self._ValidateHTTP()

    html = template.render(
        'templates/validation.html', self.validation_results)
    
    if self.request.get('use_webpage'):
      self.response.out.write(html)
    else:
      message = mail.EmailMessage(
        sender="David Choffnes <drchoffnes@gmail.com>",
        subject="Daily validation results")
      message.to = "David Choffnes <drchoffnes@gmail.com>"
      message.body = html
      message.html = html
      message.send()

  def _GetRecords(self, data_type):
    """Gets all the records for a specified type, subject
    to request parameters. """
    start_time = self.request.get('start_time')
    end_time = self.request.get('end_time')
    limit = self.request.get('limit')
    
    # manually specified for testing
    if self.TESTING:
      #limit = 100
      start_time = util.TimeToMicrosecondsSinceEpoch(util.StringToTime("2012-03-20T00:00:00Z"))
      end_time = util.TimeToMicrosecondsSinceEpoch(util.StringToTime("2012-03-21T00:00:00Z"))

    query = model.Measurement.all()

    query.filter('type = ', data_type)
    
    if start_time:
      dt = util.TimeToMicrosecondsSinceEpoch(util.StringToTime(start_time))
    else:
      dt = util.TimeToMicrosecondsSinceEpoch(datetime.datetime.utcfromtimestamp(time.time()) - datetime.timedelta(days=1)) 
    self.validation_results['start_time'] = util.MicrosecondsSinceEpochToTime(dt)
    query.filter('timestamp >=', dt)
    
    if end_time:
      dt = util.TimeToMicrosecondsSinceEpoch(util.StringToTime(end_time))
    else: 
      dt = util.TimeToMicrosecondsSinceEpoch(datetime.datetime.utcfromtimestamp(time.time()))
    self.validation_results['end_time'] = util.MicrosecondsSinceEpochToTime(dt)
    query.filter('timestamp <', dt)
    query.order('timestamp')
    if limit:
      results = query.fetch(int(limit))
    else:
      results = query
      
    self.validation_results[data_type + "_count"] = str(results.count(10000))
    
    return results
  
  def _ValidatePings(self):
    """ Validates ping data """
    results = self._GetRecords('ping')
    
    num_invalid = 0
    
    for measurement in results:
      # Need to catch case where device has been deleted
      try:
        unused_device_info = measurement.device_properties.device_info
      except db.ReferencePropertyResolveError:
        logging.exception('Device deleted for measurement %s',
                          measurement.key().id())
        # Skip this measurement
        continue

      # Need to catch case where task has been deleted
      try:
        unused_task = measurement.task
      except db.ReferencePropertyResolveError:
        measurement.task = None
        measurement.put()
      
      # Make a ping object
      p = ping.Ping(measurement.Params(), measurement.Values())
      
      valid = p.Validate()
      if not valid['valid']:
        num_invalid += 1
        
      if self.PRINT_VALUES or (self.PRINT_INVALID and not valid['valid']):
        if not self.validation_results.has_key('ping_invalid_detail'):
          self.validation_results['ping_invalid_detail'] = []
        self.validation_results['ping_invalid_detail'].append(
              'Errors: %s <br>\nTime: %s<br>\nDevice: %s<br>\nDetails: %s' % 
              (", ".join(valid['error_types']), str(measurement.timestamp),
               measurement.device_properties.device_info.id, p.PrintData()))
         
    self.validation_results['ping_invalid'] = num_invalid
    
  def _ValidateHTTP(self):
    """ Validates HTTP data """
    results = self._GetRecords('http')
    
    num_invalid = 0
    
    for measurement in results:
      # Need to catch case where device has been deleted
      try:
        unused_device_info = measurement.device_properties.device_info
      except db.ReferencePropertyResolveError:
        logging.exception('Device deleted for measurement %s',
                          measurement.key().id())
        # Skip this measurement
        continue

      # Need to catch case where task has been deleted
      try:
        unused_task = measurement.task
      except db.ReferencePropertyResolveError:
        measurement.task = None
        measurement.put()
      
      # Make a ping object
      h = http.HTTP(measurement.Params(), measurement.Values())
      
      valid = h.Validate()
      if not valid['valid']:
        num_invalid += 1
      
      if self.PRINT_VALUES or (self.PRINT_INVALID and not valid['valid']):
        if not self.validation_results.has_key('http_invalid_detail'):
          self.validation_results['http_invalid_detail'] = []
        self.validation_results['http_invalid_detail'].append(
              'Errors: %s <br>\nTime: %s<br>\nDevice: %s<br>\nDetails: %s' % 
              (", ".join(valid['error_types']), str(measurement.timestamp),
               measurement.device_properties.device_info.id, h.PrintData()))
        
    self.validation_results['http_invalid'] = num_invalid
      
  def _ValidateDnsLookup(self):
    """ Validates dns data """
    results = self._GetRecords('dns_lookup')
       
    num_invalid = 0

    for measurement in results:
      # Need to catch case where device has been deleted
      try:
        unused_device_info = measurement.device_properties.device_info
      except db.ReferencePropertyResolveError:
        logging.exception('Device deleted for measurement %s',
                          measurement.key().id())
        # Skip this measurement
        continue

      # Need to catch case where task has been deleted
      try:
        unused_task = measurement.task
      except db.ReferencePropertyResolveError:
        measurement.task = None
        measurement.put()
      
      # Make a ping object
      d = dns.DNSLookup(measurement.Params(), measurement.Values())
      
      valid = d.Validate()
      if not valid['valid']:
        num_invalid += 1
              
      if self.PRINT_VALUES or (self.PRINT_INVALID and not valid['valid']):
        if not self.validation_results.has_key('dns_invalid_detail'):
          self.validation_results['dns_invalid_detail'] = []
        self.validation_results['dns_invalid_detail'].append(
              'Errors: %s <br>\nTime: %s<br>\nDevice: %s<br>\nDetails: %s' % 
              (", ".join(valid['error_types']), str(measurement.timestamp),
               measurement.device_properties.device_info.id, d.PrintData()))

    #self.response.out.write("<p><b>Found " + str(num_invalid) + " invalid</b></p>")
    self.validation_results['dns_invalid'] = num_invalid
    
  def _ValidateTraceroutes(self):    
    results = self._GetRecords('traceroute')

    num_invalid = 0
    
    for measurement in results:
      # Need to catch case where device has been deleted
      try:
        unused_device_info = measurement.device_properties.device_info
      except db.ReferencePropertyResolveError:
        logging.exception('Device deleted for measurement %s',
                          measurement.key().id())
        # Skip this measurement
        continue

      # Need to catch case where task has been deleted
      try:
        unused_task = measurement.task
      except db.ReferencePropertyResolveError:
        measurement.task = None
        measurement.put()

      # Make a traceroute object
      tr = traceroute.Traceroute(measurement.Params(), measurement.Values())

      valid = tr.Validate()
      if not valid['valid']:
        num_invalid += 1
        
      if self.PRINT_VALUES or (self.PRINT_INVALID and not valid['valid']):
        if not self.validation_results.has_key('traceroute_invalid_detail'):
          self.validation_results['traceroute_invalid_detail'] = []
        self.validation_results['traceroute_invalid_detail'].append(
              'Errors: %s <br>\nTime: %s<br>\nDevice: %s<br>\nDetails: %s' % 
              (", ".join(valid['error_types']), str(measurement.timestamp),
               measurement.device_properties.device_info.id, tr.PrintData()))

      self.validation_results['traceroute_invalid'] = num_invalid


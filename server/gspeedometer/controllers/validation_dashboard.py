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
from django.utils.datastructures import SortedDict
from gspeedometer.controllers.validation import MeasurementValidatorFactory

""" Builds the validation webpage."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging

import operator

from django.utils import simplejson as json
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from google.appengine.ext import db

from gspeedometer import model
from gspeedometer.controllers import measurement
from gspeedometer.helpers import util

from django.utils.datastructures import SortedDict
from gspeedometer.controllers.validation import MeasurementValidatorFactory

class Dashboard(webapp.RequestHandler):
  """Controller for the dashboard view."""

  def Dashboard(self, **unused_args):
    """Main handler for the dashboard view."""

    # TODO(drc): Performance is atrocious. Needs to be refactored into smaller 
    # asynchronous datastore requests
    tscolumns = []
    for meas, name in measurement.MEASUREMENT_TYPES:
      tscolumns.append('%s' % meas)

    template_args = {
        'error_details': self.DashboardDetail(),
        'top_errors': self.CommonExceptions()
    }
    self.response.out.write(template.render(
        'templates/validationdashboard.html', template_args))

  def DashboardDetail(self, **unused_args):
    """Returns a dict of time to measurement type to count."""
    start_time = self.request.get('start_time')
    end_time = self.request.get('start_time')
    limit = self.request.get('limit')

    entries = model.ValidationEntry.all()

    if start_time:
      start_time = util.MicrosecondsSinceEpochToTime(int(start_time))
    if end_time:
      end_time = util.MicrosecondsSinceEpochToTime(int(end_time))
    if limit:
      limit = int(limit)
    else:
      limit = 1000

    # TODO(drc): Incorporate date limits.
    time_to_type_to_cnt = SortedDict()

    # group by time
    for ent in entries.fetch(limit):
      ms_time = ent.summary.timestamp_start
      meas_type = ent.summary.measurement_type
      time_to_type_to_cnt.setdefault(ms_time, dict()).setdefault(
          meas_type, {'details': []})
      time_to_type_to_cnt[ms_time][meas_type]['count'] = ent.summary.error_count
      # links to ids for eventually showing more detail
      time_to_type_to_cnt[ms_time][meas_type]['details'].append(
        [ent.measurement.key().id(),
         ent.measurement.device_properties.device_info.id])

    # now sort by time
    sorted_results = SortedDict()
    for k in sorted(time_to_type_to_cnt.iterkeys()):
      sorted_results[k] = time_to_type_to_cnt[k]

    return sorted_results

  def ErrorDetail(self, **unused_args):
    """Returns JSON encoded measurement details that are displayed when the user
    clicks on a measurement ID."""
    result_id = self.request.get('result_id') #  measurement id
    parent = self.request.get('parent') #  required parent (deviceinfo) id

    k = db.Key.from_path('DeviceInfo', parent, 'Measurement', int(result_id))

    m = model.Measurement.get(k)
    validator = MeasurementValidatorFactory.CreateValidator(m)
    self.response.out.write(json.dumps(
        {'hash': m.timestamp.strftime("%Y%m%d"),
         'type': m.type,
         'success': m.success,
         'validation_results': validator.Validate(),
         'details': validator.GetHTML()}))

  def CommonExceptions(self, **unused_args):
    """Returns a list containing the most common 10 exceptions and the number 
    of times they have been reported."""
    start_time = self.request.get('start_time')
    end_time = self.request.get('start_time')
    limit = self.request.get('limit')

    entries = model.ValidationEntry.all()
    logging.log(logging.INFO, "Found %s records" %
                model.ValidationEntry.all().count(10000))

    # TODO(drc): support date queries
    if start_time:
      start_time = util.MicrosecondsSinceEpochToTime(int(start_time))
    if end_time:
      end_time = util.MicrosecondsSinceEpochToTime(int(end_time))
    if limit:
      limit = int(limit)
    else: limit = 1000

    error_to_count = dict()

    for entry in entries.fetch(limit):
      if entry.measurement.success == False:
        # Only grab first portion of stack trace if there was an error
        if not (entry.measurement.GetValue('error') is None):
          error = "<br>".join(
              entry.measurement.GetValue('error').split("\n")[0:5])
          if not error_to_count.has_key(error):
            error_to_count[error] = 1
          else: error_to_count[error] += 1

    sorted_errors = sorted(error_to_count.iteritems(),
                           key=operator.itemgetter(1), reverse=True)
    return sorted_errors[0:10]


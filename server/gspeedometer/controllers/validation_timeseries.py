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

"""Supports printing of validation time series data."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging

import time

from django.utils import simplejson as json
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer import model
from gspeedometer.helpers import util
from gspeedometer.controllers import measurement

class Timeseries(webapp.RequestHandler):
  """Controller for the timeseries view."""

  def Timeseries(self, **unused_args):
    """Main handler for the timeseries view."""

    # This simply sets up the chart - the data is retrieved asynchronously.
    tscolumns = []
    for meas, name in measurement.MEASUREMENT_TYPES:
      tscolumns.append('%s' % meas)

    template_args = {
        'limit': config.TIMESERIES_POINT_LIMIT,
        'timeseries_columns': tscolumns,
        'types': {'record_count': 'Record count', 'error_count': 'Error count'}
    }
    self.response.out.write(template.render(
        'templates/validation_timeseries.html', template_args))

  def TimeseriesData(self, **unused_args):
    """Returns data for the timeseries view in JSON format."""
    start_time = self.request.get('start_time')
    end_time = self.request.get('start_time')
    limit = self.request.get('limit')
    sample_type = self.request.get('type')

    summaries = model.ValidationSummary.all()

    if start_time:
      start_time = util.MicrosecondsSinceEpochToTime(int(start_time))
      summaries.filter('timestamp_start > ', start_time)
    if end_time:
      end_time = util.MicrosecondsSinceEpochToTime(int(end_time))
      summaries.filter('timestamp_end < ', end_time)
    if limit:
      limit = int(limit)
    else:
      limit = 1000

    summaries.filter('timestamp_start > ', 0)
    summaries.order('timestamp_start')
    time_to_type_to_count = dict()
    tsdata = []

    # group data by timestamp for timeline printing
    for summary in summaries.fetch(limit):
      ms_time = util.TimeToMicrosecondsSinceEpoch(summary.timestamp_start) / 1e3
      if not time_to_type_to_count.has_key(ms_time):
        time_to_type_to_count[ms_time] = dict()

      if sample_type:
        time_to_type_to_count[ms_time][summary.measurement_type] = \
          getattr(summary, sample_type)
      else: time_to_type_to_count[ms_time][summary.measurement_type] = \
        summary.record_count

    # gather data into timeline-friendly output format
    for time, type_to_count in time_to_type_to_count.items():
      row = ['new Date(%d)' % time]
      for meas, name in measurement.MEASUREMENT_TYPES:
        if type_to_count.has_key(meas):
          row.append(type_to_count[meas])
        else: row.append(0)
      tsdata.append(row)

    self.response.out.write(json.dumps(tsdata))

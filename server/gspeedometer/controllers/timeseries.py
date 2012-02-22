# Copyright 2012 Google Inc.
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
#!/usr/bin/python2.4
#

"""Timeseries plotting of measurement results."""

__author__ = 'mdw@google.com (Matt Welsh)'

from django.utils import simplejson as json
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer import model
from gspeedometer.helpers import util


class Timeseries(webapp.RequestHandler):
  """Controller for the timeseries view."""

  def Timeseries(self, **unused_args):
    """Main handler for the timeseries view."""

    # This simply sets up the chart - the data is retrieved asynchronously.
    device_id = self.request.get('device_id')
    # Used to trigger permission check
    unused_device = model.DeviceInfo.GetDeviceWithAcl(device_id)
    tscolumns = []
    tscolumns.append('Signal strength (%s)' % device_id)
    tscolumns.append('Battery level (%s)' % device_id)
    template_args = {
        'limit': config.TIMESERIES_POINT_LIMIT,
        'device_id': device_id,
        'timeseries_columns': tscolumns,
    }
    self.response.out.write(template.render(
        'templates/timeseriesview.html', template_args))

  def TimeseriesData(self, **unused_args):
    """Returns data for the timeseries view in JSON format."""
    device_id = self.request.get('device_id')
    start_time = self.request.get('start_time')
    end_time = self.request.get('start_time')
    limit = self.request.get('limit')

    # Used to trigger permission check
    unused_device = model.DeviceInfo.GetDeviceWithAcl(device_id)
    if start_time:
      start_time = util.MicrosecondsSinceEpochToTime(int(start_time))
    if end_time:
      end_time = util.MicrosecondsSinceEpochToTime(int(end_time))
    if limit:
      limit = int(limit)

    measurements = model.Measurement.GetMeasurementListWithAcl(
        limit=limit, device_id=device_id,
        start_time=start_time, end_time=end_time)

    tsdata = []
    for meas in measurements:
      ms_time = util.TimeToMicrosecondsSinceEpoch(meas.timestamp) / 1000
      rssi = meas.device_properties.rssi or 0
      battery = meas.device_properties.battery_level or 0
      val = ('new Date(%d)' % ms_time, rssi, battery)
      tsdata.append(val)

    self.response.out.write(json.dumps(tsdata))

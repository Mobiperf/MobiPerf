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

"""Service to collect and visualize mobile network performance data."""

__author__ = 'mdw@google.com (Matt Welsh)'

import logging

from django.utils import simplejson as json
from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from gspeedometer import model
from gspeedometer.controllers import device
from gspeedometer.helpers import error
from gspeedometer.helpers import util

MEASUREMENT_TYPES = [('ping', 'ping'),
                     ('dns_lookup', 'DNS lookup'),
                     ('traceroute', 'traceroute'),
                     ('http', 'HTTP get'),
                     ('ndt', 'NDT measurement')]


class Measurement(webapp.RequestHandler):
  """Measurement request handler."""

  def PostMeasurement(self, **unused_args):
    """Handler used to post a measurement from a device."""
    if self.request.method.lower() != 'post':
      raise error.BadRequest('Not a POST request.')

    try:
      measurement_list = json.loads(self.request.body)
      logging.info('PostMeasurement: Got %d measurements to write',
                   len(measurement_list))
      for measurement_dict in measurement_list:
        device_info = model.DeviceInfo.get_or_insert(
            measurement_dict['device_id'])

        # Write new device properties, if present
        if 'properties' in measurement_dict:
          device_properties = model.DeviceProperties(parent=device_info)
          device_properties.device_info = device_info
          properties_dict = measurement_dict['properties']
          # TODO(wenjiezeng): Sanitize input that contains bad fields
          util.ConvertFromDict(device_properties, properties_dict)
          # Don't want the embedded properties in the Measurement object
          del measurement_dict['properties']
        else:
          # Get most recent device properties
          device_properties = device.GetLatestDeviceProperties(
              device_info, create_new_if_none=True)
        device_properties.put()

        measurement = model.Measurement(parent=device_info)
        util.ConvertFromDict(measurement, measurement_dict)
        measurement.device_properties = device_properties
        measurement.put()
    except Exception, e:
      logging.exception('Got exception posting measurements')

    logging.info('PostMeasurement: Done processing measurements')
    response = {'success': True}
    self.response.headers['Content-Type'] = 'application/json'
    self.response.out.write(json.dumps(response))

  def ListMeasurements(self, **unused_args):
    """Handles /measurements REST request."""

    # This is very limited and only supports time range and limit
    # queries. You might want to extend this to filter by other
    # properties, but for the purpose of measurement archiving
    # this is all we need.

    start_time = self.request.get('start_time')
    end_time = self.request.get('end_time')
    limit = self.request.get('limit')

    query = model.Measurement.all()

    if start_time:
      dt = util.MicrosecondsSinceEpochToTime(int(start_time))
      query.filter('timestamp >=', dt)
    if end_time:
      dt = util.MicrosecondsSinceEpochToTime(int(end_time))
      query.filter('timestamp <', dt)
    query.order('timestamp')
    if limit:
      results = query.fetch(int(limit))
    else:
      results = query

    output = util.MeasurementListToDictList(results)
    self.response.out.write(json.dumps(output))

  def MeasurementDetail(self, **unused_args):
    """Handler to display measurement detail."""
    errormsg = ''
    measurement = None
    try:
      deviceid = self.request.get('deviceid')
      measid = self.request.get('measid')

      # Need to construct complete key from ancestor path
      key = db.Key.from_path('DeviceInfo', deviceid,
                             'Measurement', int(measid))
      measurement = db.get(key)
      if not measurement:
        errormsg = 'Cannot get measurement ' + measid
        return

    finally:
      template_args = {
          'error': errormsg,
          'id': measid,
          'measurement': measurement,
          'user': users.get_current_user().email(),
          'logout_link': users.create_logout_url('/')
      }
      self.response.out.write(template.render(
          'templates/measurementdetail.html', template_args))

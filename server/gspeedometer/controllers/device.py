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
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Controller for device management."""

__author__ = 'mdw@google.com (Matt Welsh)'

import urllib
import urlparse

from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer import model
from gspeedometer.helpers import util


class Device(webapp.RequestHandler):
  """Controller to handle device management."""

  def DeviceDetail(self, **unused_args):
    """Query for a specific device."""
    errormsg = None

    device_id = self.request.get('device_id')
    device = model.DeviceInfo.GetDeviceWithAcl(device_id)
    try:
      if not device:
        errormsg = 'Device %s not found' % device_id
        template_args = {
            'error': errormsg,
            'user': users.get_current_user().email(),
            'logout_link': users.create_logout_url('/')
        }
        self.response.out.write(template.render(
            'templates/devicedetail.html', template_args))
        return

      # Get set of properties associated with this device
      query = device.deviceproperties_set
      query.order('-timestamp')
      properties = query.fetch(config.NUM_PROPERTIES_IN_LIST)

      # Get current tasks assigned to this device
      cur_schedule = [device_task.task for device_task
                      in device.devicetask_set]

      # Get measurements
      cursor = self.request.get('measurement_cursor')
      if self.request.get('all') == '1':
        query = db.GqlQuery('SELECT * FROM Measurement '
                            'WHERE ANCESTOR IS :1 '
                            'ORDER BY timestamp DESC',
                            device.key())
      else:
        query = db.GqlQuery('SELECT * FROM Measurement '
                            'WHERE ANCESTOR IS :1 AND success = TRUE '
                            'ORDER BY timestamp DESC',
                            device.key())
      if cursor:
        query.with_cursor(cursor)

      measurements = query.fetch(config.NUM_MEASUREMENTS_IN_LIST)
      # If there are more measurements to show, give the user a cursor
      if len(measurements) == config.NUM_MEASUREMENTS_IN_LIST:
        cursor = query.cursor()
        parsed_url = list(urlparse.urlparse(self.request.url))
        url_query_dict = {'device_id': device_id,
                          'measurement_cursor': cursor,
                          'all': self.request.get('all')}
        parsed_url[4] = urllib.urlencode(url_query_dict)
        more_measurements_link = urlparse.urlunparse(parsed_url)
      else:
        more_measurements_link = None

      template_args = {
          'error': errormsg,
          'device_id': device_id,
          'dev': device,
          'properties': properties,
          'measurements': measurements,
          'more_measurements_link': more_measurements_link,
          'schedule': cur_schedule,
          'user': users.get_current_user().email(),
          'logout_link': users.create_logout_url('/'),
      }
      self.response.out.write(template.render(
          'templates/devicedetail.html', template_args))
    except:
      raise

  def Delete(self, **unused_args):
    """Delete a specific device."""
    errormsg = None

    device_id = self.request.get('device_id')
    device = model.DeviceInfo.GetDeviceWithAcl(device_id)
    if not device:
      errormsg = 'Device %s not found' % device_id
      template_args = {
          'error': errormsg,
          'user': users.get_current_user().email(),
          'logout_link': users.create_logout_url('/')
      }
      self.response.out.write(template.render(
          'templates/devicedelete.html', template_args))
      return
    else:
      if self.request.get('confirm') == '1':
        # Do the deletion itself.
        device.delete()
        self.redirect('/')
        return
      else:
        # Not confirmed
        template_args = {
            'device_id': device_id,
            'dev': device,
            'user': users.get_current_user().email(),
            'logout_link': users.create_logout_url('/')
        }
        self.response.out.write(template.render(
            'templates/devicedelete.html', template_args))
        return


def GetLatestDeviceProperties(device_info, create_new_if_none=False):
  """Retrieve the latest device properties corresponding to this device_info.

  Arguments:
    device_info: The DeviceInfo object on which to retrieve the properties.
    create_new_if_none: Whether to create a new DeviceProperties if none
      exists.
  Returns:
    A DeviceProperties object, or None.
  """
  query = device_info.deviceproperties_set
  query.order('-timestamp')
  device_properties_list = query.fetch(1)
  if not device_properties_list:
    if create_new_if_none:
      device_properties = model.DeviceProperties(parent=device_info)
      device_properties.device_info = device_info
      return device_properties
    else:
      return None
  else:
    return device_properties_list[0]

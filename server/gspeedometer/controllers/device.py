#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.
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

from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer import model


class Device(webapp.RequestHandler):
  """Controller to handle device management."""

  def DeviceDetail(self, **unused_args):
    """Query for a specific device."""
    errormsg = None

    device_id = self.request.get('device_id')
    device = model.DeviceInfo.get_by_key_name(device_id)
    try:
      if not device:
        errormsg = 'Device %s not found' % device_id
        template_args = {
            'error': errormsg,
            'user': users.get_current_user().email(),
            'logout_link': users.create_logout_url('/')
        }
        return

      # Get set of properties associated with this device
      query = device.deviceproperties_set
      query.order('-timestamp')
      properties = query.fetch(config.NUM_PROPERTIES_IN_LIST)

      # Get current tasks assigned to this device
      cur_schedule = [device_task.task for device_task
                      in device.devicetask_set]

      measurements = db.GqlQuery('SELECT * FROM Measurement '
                                 'WHERE ANCESTOR IS :1 '
                                 'ORDER BY timestamp DESC',
                                 device.key())

      # Get measurements - a little tricky as we have to scan all device
      # properties and then all associated measurements
      #measurements = []
      #for prop in device.deviceproperties_set:
      #  for meas in prop.measurement_set:
      #    measurements.append(meas)
      #    if len(measurements) == config.NUM_MEASUREMENTS_IN_LIST:
      #      break
      #  if len(measurements) == config.NUM_MEASUREMENTS_IN_LIST:
      #    break

      template_args = {
          'error': errormsg,
          'device_id': device_id,
          'dev': device,
          'properties': properties,
          'measurements': measurements,
          'schedule': cur_schedule,
          'user': users.get_current_user().email(),
          'logout_link': users.create_logout_url('/')
      }
    finally:
      self.response.out.write(template.render(
          'templates/devicedetail.html', template_args))

  def Delete(self, **unused_args):
    """Handler to delete a device."""
    device_id = self.request.get('device_id')
    device = model.DeviceInfo.get(device_id)
    if not device:
      self.response.out.write('Device not found.')
      return
    device.delete()

    template_args = {
        'message': 'Device ' + device_id + ' deleted'
    }
    self.response.out.write(template.render(
        'templates/home.html', template_args))


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

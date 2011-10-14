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

import datetime

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
      query = db.GqlQuery('SELECT * FROM Measurement '
                          'WHERE ANCESTOR IS :1 '
                          'ORDER BY timestamp DESC',
                          device.key())
      measurements = query.fetch(config.NUM_MEASUREMENTS_IN_LIST)

      # Get battery info for the past 7 days
      now = datetime.datetime.utcnow()
      end_date = datetime.date(now.year, now.month, now.day)
      start_date = end_date - datetime.timedelta(days=7)

      template_args = {
          'error': errormsg,
          'device_id': device_id,
          'dev': device,
          'properties': properties,
          'measurements': measurements,
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
        # Do the deletion
        # XXX XXX XXX MDW - What happens if we do this?
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

  def _GetBatteryInfo(self, device,
                      start_date,
                      end_date):
    batteryinfo_list = []
    property_query = device.deviceproperties_set
    end_time = datetime.datetime(end_date.year,
                                 end_date.month,
                                 end_date.day)
    start_time = datetime.datetime(start_date.year,
                                   start_date.month,
                                   start_date.day)
    min_time_gap = datetime.timedelta(
        hours=config.BATTERY_INFO_INTERVAL_HOUR)
    property_query.filter('timestamp >=', start_time)
    property_query.filter('timestamp <=', end_time)
    property_query.order('-timestamp')

    last_timestamp = end_time

    for prop in property_query:
      # Show battery info every min_time_gap hours
      if (hasattr(prop, 'battery_level') and
          last_timestamp - prop.timestamp > min_time_gap):
        batteryinfo_list.append(
            '[new Date(%d), %d]' % (
                util.TimeToMicrosecondsSinceEpoch(prop.timestamp) / 1000,
                prop.battery_level))
        last_timestamp = prop.timestamp

    return batteryinfo_list


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

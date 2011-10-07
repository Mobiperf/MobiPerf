#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service to collect and visualize mobile network performance data."""

__author__ = 'mdw@google.com (Matt Welsh)'

import logging

from django.utils import simplejson as json
from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext import webapp

from gspeedometer import model
from gspeedometer.helpers import error
from gspeedometer.helpers import util


class Checkin(webapp.RequestHandler):
  """Checkin request handler."""

  def Checkin(self, **unused_args):
    """Handler for checkin requests."""
    if self.request.method.lower() != 'post':
      raise error.BadRequest('Not a POST request.')

    checkin = json.loads(self.request.body)
    logging.info('Got checkin: %s', self.request.body)

    try:
      # Extract DeviceInfo
      device_info = model.DeviceInfo.get_or_insert(checkin['id'])

      device_info.user = users.get_current_user()
      # Don't want the embedded properties in the device_info structure
      device_info_dict = dict(checkin)
      del device_info_dict['properties']
      util.ConvertFromDict(device_info, device_info_dict)
      device_info.put()

      # Extract DeviceProperties
      device_properties = model.DeviceProperties(parent=device_info)
      device_properties.device_info = device_info
      util.ConvertFromDict(device_properties, checkin['properties'])
      device_properties.put()

      logging.info('Created device properties: ' + repr(device_properties))
      logging.info('Associated device info: ' +
                  repr(device_properties.device_info))

      device_schedule = GetDeviceSchedule(device_properties)
      self.response.headers['Content-Type'] = 'application/json'
      self.response.out.write(EncodeScheduleAsJson(device_schedule))
      logging.info('Sent checkin response: %s',
                  EncodeScheduleAsJson(device_schedule))
    except Exception, e:
      logging.exception('Got exception during checkin', e)
      self.response.headers['Content-Type'] = 'application/json'
      self.response.out.write(json.dumps([]))


def GetDeviceSchedule(device_properties):
  """Return entries from the global schedule that match this device."""

  matched = set()

  schedule = model.Task.all()
  for task in schedule:
    logging.info('Matching task: %s', task)
    if not task.filter:
      logging.info('Task matched with no filter')
      matched.add(task)
    else:
      # Does the filter match this device?
      logging.info('Checking filter: %s', task.filter)
      devices = []

      # Match against DeviceProperties
      try:
        matching_device_properties = model.DeviceProperties.gql(
            'WHERE ' + task.filter)
        devices += [dp.device_info for dp in matching_device_properties]
      except db.BadQueryError:
        logging.warn('Bad filter exression %s', task.filter)

      # Match against DeviceInfo
      try:
        matching_device_info = model.DeviceInfo.gql(
            'WHERE ' + task.filter)
        devices += matching_device_info
      except db.BadQueryError:
        logging.warn('Bad filter exression %s', task.filter)

      for dev in devices:
        if dev.id == device_properties.device_info.id:
          logging.info('Filter matched %s',
                       device_properties.device_info.id)
          matched.add(task)

  # Un-assign all current tasks from this device
  for dt in device_properties.device_info.devicetask_set:
    dt.delete()

  # Assign matched tasks to this device
  for task in matched:
    logging.info('Assigning: %s', task)
    device_task = model.DeviceTask()
    device_task.task = task
    device_task.device_info = device_properties.device_info
    device_task.put()

  return matched


def EncodeScheduleAsJson(schedule):
  """Given a list of Tasks, return a JSON string encoding the schedule."""
  output = []
  for task in schedule:
    # Don't send the user, tag, or filter fields with the schedule
    output_task = util.ConvertToDict(
        task, exclude_fields=['user', 'tag', 'filter'])
    # Need to add the parameters and key fields
    output_task['parameters'] = task.Params()
    output_task['key'] = str(task.key().id_or_name())
    output.append(output_task)

  return json.dumps(output)

#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Data model for the Speedometer service."""

__author__ = 'mdw@google.com (Matt Welsh)'

import logging

from google.appengine.ext import db
from gspeedometer.helpers import util


class DeviceInfo(db.Model):
  """Represents the static properties of a given device."""

  # The unique ID of this device, also used as the key
  id = db.StringProperty()
  # The owner of the device
  user = db.UserProperty()
  # Manufacturer, model, and OS name
  manufacturer = db.StringProperty()
  model = db.StringProperty()
  os = db.StringProperty()

  def last_update(self):
    query = self.deviceproperties_set
    query.order('-timestamp')
    try:
      return query.fetch(1)[0]
    except IndexError:
      logging.exception("There are no device properties associated with the given device");
      return None


  def num_updates(self):
    query = self.deviceproperties_set
    return query.count()

  def __str__(self):
    return 'DeviceInfo <id %s, user %s, device %s-%s-%s>' % (
        self.id, self.user, self.manufacturer, self.model, self.os)


class DeviceProperties(db.Model):
  """Represents the dynamic properties of a given device."""

  # Reference to the corresponding DeviceInfo
  device_info = db.ReferenceProperty(DeviceInfo)
  # Speedometer app version
  app_version = db.StringProperty()
  # Timestamp
  timestamp = db.DateTimeProperty(auto_now_add=True)
  # IP address
  ip_address = db.StringProperty()
  # OS version
  os_version = db.StringProperty()
  # Location
  location = db.GeoPtProperty()
  # How location was acquired (e.g., GPS)
  location_type = db.StringProperty()
  # Network type (e.g., WiFi, UMTS, etc.)
  network_type = db.StringProperty()
  # Carrier
  carrier = db.StringProperty()

  def JSON_DECODE_location(self, inputval):
    lat = float(inputval['latitude'])
    lon = float(inputval['longitude'])
    self.location = db.GeoPt(lat, lon)

  def JSON_DECODE_timestamp(self, inputval):
    # TODO(wenjie): Update this to use util.MicrosecondsSinceEpochToTime,
    # and update the Android app to use that format
    self.timestamp = util.StringToTime(inputval)

  def __str__(self):
    return 'DeviceProperties <device %s>' % self.device_info


class Task(db.Expando):
  """Represents a measurement task."""

  # When created
  created = db.DateTimeProperty()
  # Who created this task
  user = db.UserProperty()
  # Measurement type
  type = db.StringProperty()
  # Tag for this measurement task
  tag = db.StringProperty()
  # Query filter to match devices against this task
  filter = db.StringProperty()

  # Start and end times
  start_time = db.DateTimeProperty()
  end_time = db.DateTimeProperty()

  # Interval and count
  interval_sec = db.FloatProperty()
  count = db.IntegerProperty()
  # Priority - larger values represent higher priority
  priority = db.IntegerProperty()

  def GetParam(self, key):
    """Return the measurement parameter indexed by the given key."""
    return self._dynamic_properties.get('mparam_' + key, None)

  def Params(self):
    """Return a dict of all measurement parameters."""
    return dict((k[len('mparam_'):], self._dynamic_properties[k])
                for k in self.dynamic_properties() if k.startswith('mparam_'))

  def JSON_DECODE_parameters(self, input_dict):
    for k, v in input_dict.items():
      setattr(self, 'mparam_' + k, v)


class Measurement(db.Expando):
  """Represents a single measurement by a single end-user device."""

  # Reference to the corresponding device properties
  device_properties = db.ReferenceProperty(DeviceProperties)
  # Measurement type
  type = db.StringProperty()
  # Timestamp
  timestamp = db.DateTimeProperty(auto_now_add=True)
  # Whether measurement was successful
  success = db.BooleanProperty()
  # Optional corresponding task
  task = db.ReferenceProperty(Task)

  def GetParam(self, key):
    """Return the measurement parameter indexed by the given key."""
    return self._dynamic_properties.get('mparam_' + key, None)

  def Params(self):
    """Return a dict of all measurement parameters."""
    return dict((k[len('mparam_'):], self._dynamic_properties[k])
                for k in self.dynamic_properties() if k.startswith('mparam_'))

  def GetValue(self, key):
    """Return the measurement value indexed by the given key."""
    return self._dynamic_properties.get('mval_' + key, None)

  def Values(self):
    """Return a dict of all measurement values."""
    return dict((k[len('mval_'):], self._dynamic_properties[k]) for
                k in self.dynamic_properties() if k.startswith('mval_'))

  def JSON_DECODE_timestamp(self, inputval):
    # TODO(wenjie): Update this to use util.MicrosecondsSinceEpochToTime,
    # and update the Android app to use that format
    self.timestamp = util.StringToTime(inputval)

  def JSON_DECODE_parameters(self, input_dict):
    for k, v in input_dict.items():
      setattr(self, 'mparam_' + k, v)

  def JSON_DECODE_values(self, input_dict):
    for k, v in input_dict.items():
      # body and headers can be fairly long. Use the Text data type instead
      if (k == "body" or k == "headers"):
        setattr(self, 'mval_' + k, db.Text(v))
      else:
        setattr(self, 'mval_' + k, v)

  def JSON_DECODE_task_key(self, task_key):
    # task_key is optional and can be None.
    # Look up the task_key and set the 'task' field accordingly.
    # If the task does not exist, just don't set this field
    if task_key is None:
      return

    task = Task.get_by_id(int(task_key))
    if not task:
      return
    self.task = task

  def __str__(self):
    return 'Measurement <device %s, type %s>' % (
        self.device_properties, self.type)


class DeviceTask(db.Model):
  """Represents a task currently assigned to a given device."""
  task = db.ReferenceProperty(Task)
  device_info = db.ReferenceProperty(DeviceInfo)

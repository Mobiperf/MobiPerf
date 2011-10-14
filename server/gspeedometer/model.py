#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Data model for the Speedometer service."""

__author__ = 'mdw@google.com (Matt Welsh)'

import logging

from google.appengine.api import users
from google.appengine.ext import db
from gspeedometer import config
from gspeedometer.helpers import acl
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
      logging.exception('There are no device properties associated with '
                        'the given device')
      return None

  def num_updates(self):
    query = self.deviceproperties_set
    return query.count()

  def __str__(self):
    return 'DeviceInfo <id %s, user %s, device %s-%s-%s>' % (
        self.id, self.user, self.manufacturer, self.model, self.os)

  def LastUpdateTime(self):
    lastup = self.last_update()
    if not lastup:
      return None
    return lastup.timestamp

  @classmethod
  def GetDeviceListWithAcl(cls):
    """Return a query for devices that can be accessed by the current user."""
    all_devices = cls.all()
    if acl.UserIsAdmin():
      return all_devices
    else:
      return all_devices.filter("user =", users.get_current_user())

  @classmethod
  def GetDeviceWithAcl(cls, device_id):
    device = cls.get_by_key_name(device_id)
    if device and (acl.UserIsAdmin() or
                   device.user == users.get_current_user()):
      return device
    else:
      raise RuntimeError('User cannot access device %s', device_id)


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
  # Battery level
  battery_level = db.IntegerProperty()
  # Battery charging status
  is_battery_charging = db.BooleanProperty()
  # Cell information represented as a string in the form of
  # 'LAC,CID,RSSI;LAC,CID,RSSI;...', where each semicolon separated
  # triplet 'LAC,CID,RSSI' are the Location Area Code, cell ID, and RSSI for
  # one of the cell towers in range
  cell_info = db.StringProperty()
  # Receive signal strength of the current cellular connection
  rssi = db.IntegerProperty()

  def JSON_DECODE_location(self, inputval):
    lat = float(inputval['latitude'])
    lon = float(inputval['longitude'])
    self.location = db.GeoPt(lat, lon)

  def JSON_DECODE_timestamp(self, inputval):
    try:
      self.timestamp = util.MicrosecondsSinceEpochToTime(int(inputval))
    except ValueError:
      logging.exception('Error occurs while converting timestamp '
                        '%s to integer', inputval)
      self.timestamp = None

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

  @classmethod
  def GetMeasurementListWithAcl(cls, limit=None, device_id=None,
                                start_time=None, end_time=None):
    """Return a list of measurements that are accessible by the current user."""
    user = users.get_current_user()
    query = cls.all()
    query.order('-timestamp')

    if device_id:
      device = DeviceInfo.GetDeviceWithAcl(device_id)
      query.ancestor(device.key())
    if start_time:
      query.filter('timestamp >=', start_time)
    if end_time:
      query.filter('timestamp <=', end_time)

    # This query will work if either the device is specified
    # or the user is an admin
    if device_id or acl.UserIsAdmin():
      if limit:
        return query.fetch(limit)
      else:
        return query.fetch(config.QUERY_FETCH_LIMIT)
    else:
      # Need to check each result to see if user is allowed to access
      # this device.
      retval = []
      for measurement in query.fetch(config.QUERY_FETCH_LIMIT):
        # Need to catch case where device has been deleted
        try:
          device_info = measurement.device_properties.device_info
          if device_info.user == user:
            retval.append(measurement)
            if limit and len(retval) == limit:
              return retval

        except db.ReferencePropertyResolveError:
          logging.exception('Device deleted for measurement %s',
                            measurement.key().id())
          # Skip this measurement
          continue

      return retval

  def GetTaskID(self):
    try:
      if not self.task:
        return None
      taskid = self.task.key().id()
      return taskid
    except db.ReferencePropertyResolveError:
      logging.exception('Cannot resolve task for measurement %s',
                        self.key().id())
      self.task = None
      self.put()
      return None

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
    try:
      self.timestamp = util.MicrosecondsSinceEpochToTime(int(inputval))
    except ValueError:
      logging.exception('Error occurs while converting timestamp '
                        '%s to integer', inputval)
      self.timestamp = None

  def JSON_DECODE_parameters(self, input_dict):
    for k, v in input_dict.items():
      setattr(self, 'mparam_' + k, v)

  def JSON_DECODE_values(self, input_dict):
    for k, v in input_dict.items():
      # body and headers can be fairly long. Use the Text data type instead
      if k == 'body' or k == 'headers':
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

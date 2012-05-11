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

"""Utility functions for the Mobiperf service."""

__author__ = 'mdw@google.com (Matt Welsh)'

import cgi
import datetime
import logging
import random
import sys
import time
from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.ext import db
from django.utils import simplejson as json


def StringToTime(thestr):
  """Convert an ISO8601 timestring into a datetime object."""
  try:
    strtime, extra = thestr.split('.')
  except:
    # Must not be a '.' in the string
    strtime = thestr[:-1]  # Get rid of 'Z' at end
    extra = 'Z'
  dt = datetime.datetime(*time.strptime(strtime, "%Y-%m-%dT%H:%M:%S")[0:6])
  # Strip 'Z' off of end
  if (extra[-1] != 'Z'): raise ValueError, "Timestring does not end in Z"
  usecstr = extra[:-1]
  # Append extra zeros to end of usecstr if needed
  while (len(usecstr) < 6):
    usecstr = usecstr + '0'
  usec = int(usecstr)
  dt = dt.replace(microsecond=usec)
  return dt


def TimeToString(dt):
  """Convert a DateTime object to an ISO8601-encoded string."""
  return dt.isoformat() + 'Z'


def MicrosecondsSinceEpochToTime(microsec_since_epoch):
  """Convert microseconds since epoch UTC to a datetime object."""
  sec = int(microsec_since_epoch / 1000000)
  usec = int(microsec_since_epoch % 1000000)
  dt = datetime.datetime.utcfromtimestamp(sec)
  dt = dt.replace(microsecond=usec)
  return dt


def TimeToMicrosecondsSinceEpoch(dt):
  """Convert a datetime object to microseconds since the epoch UTC."""
  epoch = datetime.datetime(1970, 1, 1)
  diff = dt - epoch
  microsec_since_epoch = int(((diff.days * 86400) + (diff.seconds)) * 1000000)
  microsec_since_epoch += diff.microseconds
  return microsec_since_epoch


_SIMPLE_TYPES = (int, long, float, bool, dict, basestring, list)


def ConvertToDict(model, include_fields=None, exclude_fields=None,
                  timestamps_in_microseconds=False):
  """Convert an AppEngine Model object to a Python dict ready for json dump.

     For each property in the model, set a value in the returned dict
     with the property name as its key.
  """
  output = {}
  for key, prop in model.properties().iteritems():
    if include_fields is not None and key not in include_fields: continue
    if exclude_fields is not None and key in exclude_fields: continue
    value = getattr(model, key)
    if value is None or isinstance(value, _SIMPLE_TYPES):
      output[key] = value
    elif isinstance(value, datetime.date):
      if timestamps_in_microseconds:
        output[key] = TimeToMicrosecondsSinceEpoch(value)
      else:
        output[key] = TimeToString(value)
    elif isinstance(value, db.GeoPt):
      output[key] = {'latitude': value.lat, 'longitude': value.lon}
    elif isinstance(value, db.Model):
      output[key] = ConvertToDict(value, include_fields, exclude_fields,
                                  timestamps_in_microseconds)
    elif isinstance(value, users.User):
      output[key] = value.email()
    else:
      raise ValueError('cannot encode ' + repr(prop))
  return output


def ConvertToJson(model, include_fields=None, exclude_fields=None):
  """Convert an AppEngine Model object to a JSON-encoded string."""
  return json.dumps(ConvertToDict(model, include_fields, exclude_fields))


def ConvertFromDict(model, input_dict, include_fields=None,
                    exclude_fields=None):
  """Fill in Model fields with values from a dict.

     For each key in the dict, set the value of the corresponding field
     in the given Model object to that value.

     If the Model implements a method 'JSON_DECODE_key' for a given key 'key',
     this method will be invoked instead with an argument containing
     the value. This allows Model subclasses to override the decoding
     behavior on a per-key basis.
  """
  for k, v in input_dict.items():
    if include_fields is not None and k not in include_fields: continue
    if exclude_fields is not None and k in exclude_fields: continue
    if hasattr(model, 'JSON_DECODE_' + k):
      method = getattr(model, 'JSON_DECODE_' + k)
      method(v)
    else:
      setattr(model, k, v)

def MeasurementListToDictList(measurement_list, include_fields=None,
    exclude_fields=None):
  """Converts a list of measurement entities into a list of dictionaries.

  Given a list of measuerment model objects from the datastore, this method
  will convert that list into a list of python dictionaries that can then
  be serialized.

  Args:
    measurement_list: A list of measurement entities from the datastore.
    include_fields: A list of attributes for the entities that should be
        included in the serialized form.
    exclude_fields: A list of attributes for the entities that should be
        excluded in the serialized form.
  
  Returns:
    A list of dictionaries representing the list of measurement entities.

  Raises:
    db.ReferencePropertyResolveError: handled for the cases where a device has
        been deleted and where task has been deleted.
    No New exceptions generated here.
  """
  #TODO(mdw) Unit test needed.
  #TODO(gavaletz) make this iterate over a query instead of a list.
  output = list()
  for measurement in measurement_list:
    # Need to catch case where device has been deleted
    try:
      unused_device_info = measurement.device_properties.device_info
    except db.ReferencePropertyResolveError:
      logging.exception('Device deleted for measurement %s',
          measurement.key().id())
      # Skip this measurement
      continue

    # Need to catch case where task has been deleted
    try:
      unused_task = measurement.task
    except db.ReferencePropertyResolveError:
      measurement.task = None
      measurement.put()

    mdict = ConvertToDict(measurement, include_fields, exclude_fields,
        timestamps_in_microseconds=True)

    # Fill in additional fields
    mdict['id'] = str(measurement.key().id())
    mdict['parameters'] = measurement.Params()
    mdict['values'] = measurement.Values()

    if 'task' in mdict and mdict['task'] is not None:
      mdict['task']['id'] = str(measurement.GetTaskID())
      mdict['task']['parameters'] = measurement.task.Params()

    output.append(mdict)
  return output

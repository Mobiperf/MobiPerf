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

__author__ = ('mdw@google.com (Matt Welsh), '
              'drchoffnes@gmail.com (David Choffnes)')

import logging

from django.utils import simplejson as json
from django.utils.datastructures import SortedDict
from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from gspeedometer import model
from gspeedometer.controllers import device
from gspeedometer.helpers import error
from gspeedometer.helpers import util

# list of supported measurement types
MEASUREMENT_TYPES = [('ping', 'ping'),
                     ('dns_lookup', 'DNS lookup'),
                     ('traceroute', 'traceroute'),
                     ('http', 'HTTP get')]

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
        # Change device id such that it is anonymized, but preserve the TAC.
        measurement_dict['tac'] = util.GetTypeAllocationCode(
            measurement_dict['device_id'])
        measurement_dict['device_id'] = util.HashDeviceId(
            measurement_dict['device_id'])

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


# TODO(drc): Move this to generic measurement class when code from related 
# branch gets merged into master.
class MeasurementType:
  """Maps datastore entity and field names to human-readable ones."""

  # name of the measurement type in the datastore (e.g., ping)
  kind = "generic_measurement"
  # human-readable name of measurement type
  description = "Generic measurement"
  # dictionary of field names (as stored in datastore) to human-readable 
  # descriptions of those fields (for printing in a form)
  field_to_description = SortedDict()

  def __init__(self, kind, description, field_to_description):
    self.kind = kind
    self.description = description
    self.field_to_description = field_to_description

  @staticmethod
  def GetDefaultMeasurement():
    """Utility method for getting the default type to show in the scheduler."""
    return MeasurementType.GetMeasurement(MEASUREMENT_TYPES[0][0])

  @staticmethod
  def GetMeasurement(measurement_type):
    """Factory method for getting measurement objects for display in 
    measurement scheduler. 
    
    Note that we need to pass a SortedDict to ensure that an iterator 
    returns fields in the proper order for display.
       
    Args:
      measurement_type: Name of measurement type used in datastore.
      
    Returns:
      A MeasurementType object with measurement-specific details.
    """
    if measurement_type == 'ping':
      return MeasurementType(
          'ping', 'ping', SortedDict([('target', 'Target (IP or hostname)'),
          ('location_update_distance', 'Location update distance (m)'),
          ('trigger_location_update', 'Trigger location update (bool)'),
          ('ping_timeout_sec', 'Ping timeout (seconds)'),
          ('packet_size_byte', 'Ping packet size (bytes)')]))
    elif measurement_type == 'dns_lookup':
      return MeasurementType(
          'dns_lookup', 'DNS lookup',
          SortedDict([('target', 'Target (IP or hostname)'),
          ('location_update_distance', 'Location update distance (m)'),
          ('trigger_location_update', 'Trigger location update (bool)'),
           ('server', 'DNS server')]))
    elif measurement_type == 'traceroute':
      return MeasurementType(
          'traceroute', 'traceroute',
          SortedDict([('target', 'Target (IP or hostname)'),
          ('location_update_distance', 'Location update distance (m)'),
          ('trigger_location_update', 'Trigger location update (bool)'),
          ('max_hop_count', 'Traceroute max hop count'),
          ('pings_per_hop', 'Traceroute pings per hop')]))
    elif measurement_type == 'http':
      return MeasurementType(
          'http', 'HTTP get', SortedDict([('url', 'HTTP URL'),
          ('location_update_distance', 'Location update distance (m)'),
          ('trigger_location_update', 'Trigger location update (bool)'),
          ('headers', 'HTTP headers'), ('method', 'HTTP method')]))
    else:
      raise RuntimeError('Invalid measurement type: %s' % measurement_type)

# Copyright 2012 University of Washington.
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
#

"""Simple class for mapping measurement types to their fields."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

from django.utils.datastructures import SortedDict
from gspeedometer.controllers import measurement

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
  def Get_Default_Measurement():
    """Utility method for getting the default type to show in the scheduler."""
    return MeasurementType.Get_Measurement(measurement.MEASUREMENT_TYPES[0][0])
  
  @staticmethod
  def Get_Measurement(measurement_type):
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

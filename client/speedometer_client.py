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
#!/usr/bin/python2.6
#

"""Simple Python command-line client to Speedometer."""

__author__ = 'royman@google.com (Roy McElmurry)'

import datetime
import json
import logging
import optparse
import random
import sys

import appengine_client


parser = optparse.OptionParser()

parser.add_option('--speedometer_server',
                  default='http://speedometer.googleplex.com',
                  help='Speedometer server')

parser.add_option('--use_local_dev_server', action='store_true',
                  default=False, help='Connect to a local development server')

parser.add_option('--username', default=None,
                  help='Username to login with')
parser.add_option('--password', default=None,
                  help='Application-specific password')

parser.add_option('--list_devices', action='store_true', default=False,
                  help='List accessible devices')

parser.add_option('--fake_checkin', action='store_true', default=False,
                  help='Perform a fake device checkin')
parser.add_option('--fake_post_measurements', action='store_true',
                  default=False, help='Perform fake measurement posts')

options, unused_leftover_args = parser.parse_args(args=sys.argv)


def TimeToMicrosecondsSinceEpoch(dt):
  """Convert a datetime object to microseconds since the epoch UTC.

  Args:
    dt: A datetime.datetime object.
  Returns:
    A long in microseconds since the epoch UTC.
  """
  epoch = datetime.datetime(1970, 1, 1)
  diff = dt - epoch
  microsec_since_epoch = int(((diff.days * 86400) + (diff.seconds)) * 1000000)
  microsec_since_epoch += diff.microseconds
  return microsec_since_epoch


class SpeedometerClient(object):
  """Encapsulates an rpc agent with common tasks."""

  def __init__ (self, speedometer_server=None, use_local_dev_server=None,
                auth_function=None):
    if speedometer_server is None:
      speedometer_server = options.speedometer_server
    if use_local_dev_server is None:
      use_local_dev_server = options.use_local_dev_server

    if use_local_dev_server:
      rpc_agent = appengine_client.DevAppServerHttpRpcServer(speedometer_server)
    else:
      if auth_function is None:
        auth_function = lambda: (options.username, options.password)
      rpc_agent = appengine_client.HttpRpcServer(
          speedometer_server,
          auth_function=auth_function)

    self.rpc_agent = rpc_agent

  def GetDevices(self):
    """Returns a list of available devices."""
    devices_json = self.rpc_agent.Send('/devices')
    devices = json.loads(devices_json)
    logging.info('Found %d devices', len(devices))
    return devices

  def _FakeDeviceProperties(self):
    """Return a dict containing a fake device_properties record."""
    retval = {
        'app_version': '0.1',
        'ip_address': '127.42.38.1',
        'os_version': '1.0',
        'carrier': 'Fake carrier',
        'network_type': 'Fake network',
        'location': {
            'latitude': 47.6508, 'longitude': -122.3515
            },
        'location_type': 'Fake location type',
        'battery_level': random.randint(1, 100),
        'is_battery_charging': False,
        'cell_info': 'LAC1,CID1,RSSI1;LAC2,CID2,RSSI2',
        'rssi': random.randint(1, 100),
        }
    return retval

  def FakeDeviceCheckin(self, device_id):
    """Perform a fake checkin to the server."""
    payload_json = json.dumps({
        'status': 'READY',
        'id': device_id,
        'manufacturer': 'Fake manufacturer',
        'model': 'Fake model',
        'os': 'Fake OS',
        'properties': self._FakeDeviceProperties(),
    })
    response_json = self.rpc_agent.Send('/checkin', payload=payload_json)
    return json.loads(response_json)

  def FakePostMeasurement(self, device_id,
                          measurement_type='ping',
                          task_parameters=None,
                          task_values=None,
                          timestamp=None):
    """Submit a fake measurement result to the server."""
    timestamp = timestamp or datetime.datetime.utcnow()
    tsusec = TimeToMicrosecondsSinceEpoch(timestamp)

    payload_json = json.dumps([{
        'device_id': device_id,
        'timestamp': tsusec,
        'properties': self._FakeDeviceProperties(),
        'type': measurement_type,
        'success': True,
        'parameters': task_parameters or {},
        'values': task_values or {}}])
    response_json = self.rpc_agent.Send('/postmeasurement',
                                        payload=payload_json)
    return json.loads(response_json)


def main():
  client = SpeedometerClient()

  if options.list_devices:
    devices = client.GetDevices()

    for device in devices:
      print ('%(id)s,%(status)s,%(pending_count)d,%(manufacturer)s,'
             '%(model)s,%(os)s,%(os_version)s') % device

  elif options.fake_checkin:
    print client.FakeDeviceCheckin('fakedevice1')

  elif options.fake_post_measurements:
    # Generate a bunch of fake measurements
    timestamp = datetime.datetime.utcnow()
    timestamp -= datetime.timedelta(days=1)

    for x in range(10):
      device_id = 'fakedevice_%d' % x
      print client.FakeDeviceCheckin(device_id)
      for unused_y in range(25):
        timestamp += datetime.timedelta(minutes=1)
        params = {'target': 'www.faketarget.com'}
        vals = {'target_ip': '1.2.3.4',
                'ping_method': 'ping_cmd',
                'mean_rtt_ms': str(random.randint(25, 75)),
                'min_rtt_ms': str(random.randint(1, 25)),
                'max_rtt_ms': str(random.randint(75, 100)),
                'stddev_rtt_ms': str(random.randint(1, 10)),
                'packet_loss': '0.25'}
        client.FakePostMeasurement(device_id,
                                   measurement_type='ping',
                                   task_parameters=params,
                                   task_values=vals,
                                   timestamp=timestamp)

  else:
    print 'No command option specified.'
    sys.exit(1)

if __name__ == '__main__':
  main()

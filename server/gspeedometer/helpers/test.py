# Copyright 2012 Google Inc. All Rights Reserved.
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


"""Test utilities."""

__author__ = 'mdw@google.com (Matt Welsh)'

import os
import unittest2
from google.appengine.api import users
from google.appengine.ext import testbed
from gspeedometer import model


class MobiperfTest(unittest2.TestCase):
  """Test utilities."""

  TEST_USER = 'testuser@google.com'
  TEST_DEVICE_MANUFACTURER = 'fake_manufacturer'
  TEST_DEVICE_MODEL = 'fake_model'
  TEST_DEVICE_OS = 'fake_os'
  TEST_DEVICE_PROPERTIES_APP_VERSION = 'fake_app_version'
  TEST_DEVICE_PROPERTIES_OS_VERSION = 'fake_os_version'

  def setUp(self):
    # First, create an instance of the Testbed class.
    self.testbed = testbed.Testbed()
    # Then activate the testbed, which prepares the service stubs for use.
    self.testbed.activate()
    # Next, declare which service stubs you want to use.
    self.testbed.init_datastore_v3_stub()
    self.testbed.init_memcache_stub()
    self.testbed.init_user_stub()
    self._SetCurrentUser(MobiperfTest.TEST_USER)

  def tearDown(self):
    self._LogoutCurrentUser()
    self.testbed.deactivate()

  def _SetCurrentUser(self, email, user_id='123', is_admin=False):
    os.environ['USER_EMAIL'] = email or ''
    os.environ['USER_ID'] = user_id or ''
    os.environ['USER_IS_ADMIN'] = '1' if is_admin else '0'

  def _LogoutCurrentUser(self):
    self._SetCurrentUser(None, None)

  def _CreateFakeDevices(self, n=3, extra_info=None):
    """Create fake devices.

    Args:
      n: Number of devices to create.
      extra_info: optional list of dictionaries, where dictionary x
        will be written to the device properties of fake device x.

    Returns:
      A list of the created device objects.
    """
    devices = []
    for dev_num in range(n):
      dev_name = 'fakedevice%d' % dev_num

      new_device = model.DeviceInfo(key_name=dev_name)
      new_device.id = dev_name
      new_device.user = users.User(MobiperfTest.TEST_USER)
      new_device.manufacturer = MobiperfTest.TEST_DEVICE_MANUFACTURER
      new_device.model = MobiperfTest.TEST_DEVICE_MODEL
      new_device.os = MobiperfTest.TEST_DEVICE_OS
      new_device.put()

      properties = model.DeviceProperties()
      properties.device_info = new_device
      properties.app_version = MobiperfTest.TEST_DEVICE_PROPERTIES_APP_VERSION
      properties.os_version = MobiperfTest.TEST_DEVICE_PROPERTIES_OS_VERSION

      if extra_info and extra_info[dev_num]:
        for k, v in extra_info[dev_num].iteritems():
          setattr(properties, k, v)
      properties.put()
      devices.append(new_device)

    return devices

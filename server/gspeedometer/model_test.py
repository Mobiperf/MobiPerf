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


"""Tests for model.py."""

__author__ = 'mdw@google.com (Matt Welsh)'

import datetime
from gspeedometer import model
from gspeedometer.helpers import test


class ModelTest(test.MobiperfTest):
  """Tests for model.py."""

  def testDeviceInfoCreate(self):
    # Test that a DeviceInfo can be created.
    device = model.DeviceInfo(key_name='mydevice')
    device.put()
    self.assertEqual(1, len(model.DeviceInfo().all().fetch(2)))
    result = model.DeviceInfo().get_by_key_name('mydevice')
    self.assertEqual(result.key().name(), 'mydevice')

  def testDevicePropertiesCreate(self):
    # Test that a DeviceProperties can be created.
    device = self._CreateFakeDevices(n=1)[0]
    self.assertEqual(1, len(model.DeviceProperties().all().fetch(2)))
    result = device.last_update()
    self.assertEqual(result.device_info.key().name(), 'fakedevice0')
    self.assertEqual(result.device_info.manufacturer,
                     test.MobiperfTest.TEST_DEVICE_MANUFACTURER)
    self.assertEqual(result.os_version,
                     test.MobiperfTest.TEST_DEVICE_PROPERTIES_OS_VERSION)

  def testDevicePropertiesUpdate(self):
    # Test that updates to the device properties are counted correctly.
    device = self._CreateFakeDevices(n=1)[0]
    self.assertEqual(device.num_updates(), 1)
    new_properties = model.DeviceProperties()
    new_properties.device_info = device
    new_properties.put()
    self.assertEqual(device.num_updates(), 2)
    new_properties = model.DeviceProperties()
    new_properties.device_info = device
    new_properties.put()
    self.assertEqual(device.num_updates(), 3)

  def testDeviceLastUpdateTime(self):
    # Test that DeviceInfo.LastUpdateTime() works as expected.
    device = self._CreateFakeDevices(n=1)[0]
    new_properties = model.DeviceProperties()
    new_properties.device_info = device
    now = datetime.datetime.utcnow()
    new_properties.timestamp = now
    new_properties.put()
    self.assertEqual(device.LastUpdateTime(), now)

  def testTaskCreate(self):
    # Test that a Task can be created.
    task = model.Task(key_name='mytask')
    task.tag = 'faketag'
    task.put()
    self.assertEqual(1, len(model.Task().all().fetch(2)))
    result = model.Task().get_by_key_name('mytask')
    self.assertEqual(result.tag, 'faketag')

  def testTaskExpando(self):
    # Test that Task.GetParam() and Task.Params() work as expected.
    task = model.Task(key_name='mytask')
    task.mparam_foo = 'somevalue1'
    task.mparam_bar = 'somevalue2'
    task.put()

    self.assertEqual(task.GetParam('foo'), 'somevalue1')
    self.assertEqual(task.GetParam('bar'), 'somevalue2')
    params = task.Params()
    self.assertEqual(params['foo'], 'somevalue1')
    self.assertEqual(params['bar'], 'somevalue2')
    self.assertEqual(len(params), 2)

  def testMeasurementCreate(self):
    # Test that a Measurement can be created.
    measurement = model.Measurement(key_name='mymeasurement')
    measurement.type = 'sometype'
    measurement.put()
    self.assertEqual(1, len(model.Measurement().all().fetch(2)))
    result = model.Measurement().get_by_key_name('mymeasurement')
    self.assertEqual(result.type, 'sometype')
    self.assertEqual(result.GetTaskID(), None)

  def testMeasurementCreateWithTask(self):
    # Test that a Measurement can be created with a corresponding Task.
    task = model.Task()
    task.tag = 'faketag'
    task.put()
    task_id = task.key().id()
    measurement = model.Measurement(key_name='mymeasurement')
    measurement.type = 'sometype'
    measurement.task = task
    measurement.put()
    self.assertEqual(1, len(model.Measurement().all().fetch(2)))
    result = model.Measurement().get_by_key_name('mymeasurement')
    self.assertEqual(result.type, 'sometype')
    self.assertEqual(result.task.tag, 'faketag')
    self.assertEqual(result.GetTaskID(), task_id)

  def testMeasurementExpando(self):
    # Test that Measurement Expando functions work as expected.
    measurement = model.Measurement()
    measurement.mparam_foo = 'somevalue1'
    measurement.mparam_bar = 'somevalue2'
    measurement.mval_baz = 'somevalue3'
    measurement.put()

    self.assertEqual(measurement.GetParam('foo'), 'somevalue1')
    self.assertEqual(measurement.GetParam('bar'), 'somevalue2')
    self.assertEqual(measurement.GetValue('baz'), 'somevalue3')
    params = measurement.Params()
    self.assertEqual(params['foo'], 'somevalue1')
    self.assertEqual(params['bar'], 'somevalue2')
    self.assertEqual(len(params), 2)
    params = measurement.Values()
    self.assertEqual(params['baz'], 'somevalue3')
    self.assertEqual(len(params), 1)

  def testDeviceTaskCreate(self):
    # Test that a DeviceTask can be created.
    device = model.DeviceInfo(key_name='mydevice')
    device.put()
    task = model.Task()
    task.tag = 'mytag'
    task.put()
    device_task = model.DeviceTask(key_name='mydevicetask')
    device_task.device_info = device
    device_task.task = task
    device_task.put()
    self.assertEqual(1, len(model.DeviceTask().all().fetch(2)))
    result = model.DeviceTask().get_by_key_name('mydevicetask')
    self.assertEqual(result.device_info.key().name(), 'mydevice')
    self.assertEqual(result.task.tag, 'mytag')

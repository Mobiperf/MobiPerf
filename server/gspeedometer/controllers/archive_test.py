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
from gspeedometer import config, model
from sets import Set


"""Tests for controllers/archive.py."""

__author__ = ['gavaletz@google.com (Eric Gavaletz)',
              'drchoffnes@gmail.com (David Choffnes)']

import unittest2

from gspeedometer.controllers import archive


class ArchiveTest(unittest2.TestCase):
  """Tests for controllers/archive.py."""

  START = '1332979200000000'
  END = '1333065600000000'
  DEVICE = '351863040021202'

  FN_BASE = ''
  FN_BASE_S = 'S-%s' % START
  FN_BASE_E = 'E-%s' % END
  FN_BASE_D = 'I-%s' % DEVICE
  FN_BASE_SD = '%s_%s' % (FN_BASE_S, FN_BASE_D)
  FN_BASE_ED = '%s_%s' % (FN_BASE_E, FN_BASE_D)
  FN_BASE_SE = '%s_%s' % (FN_BASE_S, FN_BASE_E)
  FN_BASE_SED = '%s_%s_%s' % (FN_BASE_S, FN_BASE_E, FN_BASE_D)

  def testParametersToFileNameBase(self):
    """Test the file name base creation based on parameters.
    """
    gen_base = archive.ParametersToFileNameBase()
    self.assertEqual(gen_base, ArchiveTest.FN_BASE)
    gen_base = archive.ParametersToFileNameBase(start_time=ArchiveTest.START)
    self.assertEqual(gen_base, ArchiveTest.FN_BASE_S)
    gen_base = archive.ParametersToFileNameBase(end_time=ArchiveTest.END)
    self.assertEqual(gen_base, ArchiveTest.FN_BASE_E)
    gen_base = archive.ParametersToFileNameBase(device_id=ArchiveTest.DEVICE)
    self.assertEqual(gen_base, ArchiveTest.FN_BASE_D)
    gen_base = archive.ParametersToFileNameBase(start_time=ArchiveTest.START,
        device_id=ArchiveTest.DEVICE)
    self.assertEqual(gen_base, ArchiveTest.FN_BASE_SD)
    gen_base = archive.ParametersToFileNameBase(end_time=ArchiveTest.END,
        device_id=ArchiveTest.DEVICE)
    self.assertEqual(gen_base, ArchiveTest.FN_BASE_ED)
    gen_base = archive.ParametersToFileNameBase(start_time=ArchiveTest.START,
        end_time=ArchiveTest.END)
    self.assertEqual(gen_base, ArchiveTest.FN_BASE_SE)
    gen_base = archive.ParametersToFileNameBase(start_time=ArchiveTest.START,
        end_time=ArchiveTest.END, device_id=ArchiveTest.DEVICE)
    self.assertEqual(gen_base, ArchiveTest.FN_BASE_SED)

  def testAnonymizeFieldsRespected(self):
    """Test whether data anonymization is happening properly."""
    # Tests:
    # 1) Fields to anonymize haven't changed.
    anonymize_fields = ['user', 'ip_address', 'id']
    self.assertEqual(anonymize_fields, config.ANONYMIZE_FIELDS,
                     'Anonymize fields do not match')

    # 2) Fields to anonymize don't appear in output.
    # Get data to find range of time to test.
    query = model.Measurement.all()
    results = query.fetch(100)
    self.assertTrue(len(results) > 0, 'Empty dataset')
    start_time = None
    end_time = None
    for measurement in results:
      if not start_time or start_time > measurement.timestamp:
        start_time = measurement.timestamp
      if not end_time or end_time < measurement.timestamp:
        end_time = measurement.timestamp

    dictlist = archive.GetMeasurementDictList(None, start_time, end_time,
                                              anonymize=True)
    self.assertTrue(len(results) > 0, 'Empty dictlist')
    # Check for fields being stripped.
    for m in dictlist:
      self._CheckForKeyNotPresent(m, Set(anonymize_fields))

    # 3) Location precision is chopped properly.
    for m in dictlist:
      self._CheckForGeoResolution(m, Set(['latitude', 'longitude']))

  def testRawDataExistsWhenNotAnonymizing(self):
    """Test whether raw data is fetched properly."""
    # Get data to find range of time to test.
    query = model.Measurement.all()
    results = query.fetch(100)
    self.assertTrue(len(results) > 0, 'Empty dataset')
    start_time = None
    end_time = None
    for measurement in results:
      if not start_time or start_time > measurement.timestamp:
        start_time = measurement.timestamp
      if not end_time or end_time < measurement.timestamp:
        end_time = measurement.timestamp

    dictlist = archive.GetMeasurementDictList(None, start_time, end_time,
                                              anonymize=False)
    self.assertTrue(len(results) > 0, 'Empty dictlist')
    # Check for fields being present.
    # NOTE: Currently only the id is guaranteed to be in a data item. IP 
    # addresses are stored only by measurement-specific object (and not all of 
    # them), while user account info is NEVER retrieved by archive methods).
    anonymize_fields = Set(['id'])
    for m in dictlist:
      found_fields = self._CheckForKeyPresent(m, anonymize_fields, Set())
      self.assertSetEqual(anonymize_fields, found_fields,
          'Raw data not present in dict %s\n%s' % (m, found_fields))

  def _CheckForKeyPresent(self, dict_to_check, keys, keys_found, parent=None):
    """Fails assertion check if dict_to_check does not have a key 
       specified in keys.
       
       Returns the set of keys found in the measurement dict.
    """
    for key, value in dict_to_check.iteritems():
      if key in keys:
        keys_found.add(key)
      if keys_found == keys:
        return keys_found
      if isinstance(value , dict):
        keys_found |= self._CheckForKeyPresent(value, keys, keys_found, parent)
    return keys_found


  def _CheckForKeyNotPresent(self, dict_to_check, keys, parent=None):
    """Fails assertion check if dict_to_check has a key specified in keys."""
    for key, value in dict_to_check.iteritems():
      if parent and parent != 'task':
        self.assertNotIn(key, keys,
            'Key %s found in dict (value: %s)' % (key, value))
      if isinstance(value , dict):
        self._CheckForKeyNotPresent(value, keys, parent)

  def _CheckForGeoResolution(self, dict_to_check, keys):
    """Fails assertion check if dict_to_check has high-resolution geographic 
       coordinates.
    """
    for key, value in dict_to_check.iteritems():
      if key in keys:
        self.assertAlmostEqual(float(value),
            int(config.ANONYMIZE_LOCATION_PRECISION * float(value)) /
            float(config.ANONYMIZE_LOCATION_PRECISION), 5,
            'Location precision off %d' % float(value))
      if isinstance(value , dict):
        self._CheckForGeoResolution(value, keys)

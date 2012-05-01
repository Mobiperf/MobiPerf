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


"""Tests for controllers/archive.py."""

__author__ = 'gavaletz@google.com (Eric Gavaletz)'

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
  FN_BASE_SED  = '%s_%s_%s' % (FN_BASE_S, FN_BASE_E, FN_BASE_D)

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

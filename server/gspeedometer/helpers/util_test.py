# Copyright (c) 2012, University of Washington
# All rights reserved.
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
"""Tests for utility functions in util.py."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import unittest2
import random

from gspeedometer.helpers import util


class UtilTest(unittest2.TestCase):
  """Tests for helpers/util.py."""

  def testIMEIHash(self):
    """Test the hashing of device ID and extraction of type allocation code.
    
    This test will fail if the the type allocation code is not preserved or 
    two different IMEIs produce the same hash.
    """

    # Generate two random 15-digit numbers.
    imei1 = str(random.randint(0, 1e16 - 1)).zfill(15)
    imei2 = imei1
    while imei1 == imei2:
      imei2 = str(random.randint(0, 1e16 - 1)).zfill(15)

    tac1 = util.GetTypeAllocationCode(imei1)
    self.assertEqual(tac1, imei1[0:8], "Type allocation code mismatch")
    tac2 = util.GetTypeAllocationCode(imei2)
    self.assertEqual(tac2, imei2[0:8], "Type allocation code mismatch")

    hash1 = util.HashDeviceId(imei1)
    hash2 = util.HashDeviceId(imei2)
    self.assertNotEqual(hash1, hash2, "Hash collision")
    self.assertNotEqual(hash1, imei1[8:], "IMEI not hashed")
    self.assertNotEqual(hash2, imei2[8:], "IMEI not hashed")

"""
Copyright (c) 2012, University of Washington
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, 
are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this 
list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice, this 
list of conditions and the following disclaimer in the documentation and/or 
other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE 
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
"""

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

    # generate two random 15-digit numbers
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



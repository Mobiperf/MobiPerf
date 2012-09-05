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


"""Tests for helpers/archive_util.py."""

__author__ = 'gavaletz@google.com (Eric Gavaletz)'

import unittest2

from gspeedometer.helpers import archive_util


class ArchiveUtilTest(unittest2.TestCase):
  """Tests for helpers/archive_util.py."""
  
  FILE_CONTENT = 'Don\'t worry about people stealing an idea. If it\'s \
original, you will have to ram it down their throats.'
  FILE_CONTENT_MOD = 'Don\'t worry about people stealing an idea. If it\'s \
original, you will have 2 ram it down their throats.'

  #If any of the above strings change this needs to be updated.
  FILE_CONTENT_MD5 = '811909e0f78ebd1c4b8d50a8168fea6e'

  ARCHIVE = {'FILE_CONTENT': FILE_CONTENT, 'FILE_CONTENT_MOD': FILE_CONTENT_MOD}

  #If any of the above strings change this needs to be updated.
  ARCHIVE_ZIP_MD5 = 'bf15754aa282be5ff59d689fa14ee6c3'

  def testArchiveHash(self):
    """Test the hash.
    
    This test will fail if the type of hash generated changes.
    """
    gen_hash = archive_util.ArchiveHash(ArchiveUtilTest.FILE_CONTENT)
    gen_hash_mod = archive_util.ArchiveHash(ArchiveUtilTest.FILE_CONTENT_MOD)
    self.assertEqual(gen_hash, ArchiveUtilTest.FILE_CONTENT_MD5)
    self.assertNotEqual(gen_hash_mod, ArchiveUtilTest.FILE_CONTENT_MD5) 

  def testArchiveCompress(self):
    """Test the compression.

    This test will fail if the type of compression or the type of hash generated
    changes.  The hash should match the md5sum generated from a local file.
    """
    archive_zip = archive_util.ArchiveCompress(ArchiveUtilTest.ARCHIVE)
    gen_hash = archive_util.ArchiveHash(archive_zip)
    self.assertEqual(gen_hash, ArchiveUtilTest.ARCHIVE_ZIP_MD5)

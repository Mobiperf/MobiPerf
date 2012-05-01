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

"""Archive utility functions for the Mobiperf service.

These functions are intended to make it easy to take data from the datastore
and compress it in a file format for download or storage.

For a simple usage example see gspeeometer.controllers.archive.
"""

__author__ = 'gavaletz@google.com (Eric Gavaletz)'

import logging
import md5
import StringIO
import zipfile


def ArchiveCompress(file_dict, directory=None):
  """Compresses an archive dictionary to a zip file.

  Provided an archive dictionary and an optional directory name each key is
  combined with the directory name to create a file path and the value is used
  as the contents of the file.  The files are then compressed into a zip file
  suitable for download or storing in BigStore.

  Args:
    file_dict: A dictionary JSON strings representing the list of JSON encoded
        files.
  
  Returns:
    A zipfile of the files represented in the archive dictionary.

  Raises:
    RuntimeError: Exception raised if the zlib module is missing or if the
        StringIO stream is closed unexpectedly.
  """
  archive_stream = StringIO.StringIO()
  try:
    archive_file = zipfile.ZipFile(file=archive_stream,
      compression=zipfile.ZIP_DEFLATED, mode='w')
  except RuntimeError, e:
    logging.exception('likely missing the zlib module: ', e)
    raise e
  for member_name in file_dict:
    if directory is None:
      info = zipfile.ZipInfo(member_name)
    else:
      info = zipfile.ZipInfo('%s/%s' % (directory, member_name))
    info.external_attr = 0644 << 16L
    info.compress_type = zipfile.ZIP_DEFLATED
    try:
      archive_file.writestr(info, file_dict[member_name])
    except RuntimeError, e:
      logging.exception('likely FakeFile is closed or \'r\': ', e)
      raise e
  archive_file.close()
  archive_stream.seek(0)
  return archive_stream.getvalue()


def ArchiveHash(archive):
  """Calculates the cryptographic hash for the archive.

  We are using md5 because it can be sent to Google storage as the "Content-MD5"
  HTTP request header (used to check the integrity of the PUT operation) and it
  can be compared to the "ETag" HTTP response header so that we can compare to a
  known hash.

  Args:
    archive: The data that we want to calculate the md5 hash for.

  Returns:
    A string with the hexadecimal representation of the md5 hash of the data
    supplied in the archive_string.

  Raises:
    No exceptions handled here.
    No new exceptions generated here.
  """
  return md5.md5(archive).hexdigest()

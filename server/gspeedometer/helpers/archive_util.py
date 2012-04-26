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
serialize it, and compress it in a file format for download or storage.  The
reliability of these methods is dependent on the implementations of the
serialization methods in gspeeometer.helpers.util.

For a simple usage example see gspeeometer.controllers.archive.
"""

__author__ = 'gavaletz@google.com (Eric Gavaletz)'

import logging
import md5
import StringIO
import zipfile

from django.utils import simplejson as json

from gspeedometer import model
from gspeedometer.helpers import util


def ArchivePack(model_list, include_fields=None, exclude_fields=None):
  """Archives a list of entities from the datastore.

  Provided a list of entities from the datastore this method will convert the
  entities to JSON.  The result of this operation is a dictionary where the key
  is the name of the kind and the value is the JSON encoded list of entities of
  that kind.

  Args:
    model_list: A list of entities from the datastore.  The list can be of
        mixed kinds.
    include_fields: A list of attributes for the entities that should be
        included in the serialized form.
    exclude_fields: A list of attributes for the entities that should be
        excluded in the serialized form.
  
  Returns:
    A dictionary JSON strings representing the list of JSON encoded files.

  Raises:
    TypeError: Exception raised is the json library is unable to deal with one
        of the types in the models.  The error is logged and re-raised.
  """
  model_dict_by_kind = dict()
  for m in model_list:
    kind = m.kind()
    if not kind in model_dict_by_kind:
      model_dict_by_kind[kind] = list()
    model_dict_by_kind[kind].append(util.ConvertToDict(m, include_fields,
        exclude_fields, timestamps_in_microseconds=True))
  json_data_by_kind = dict()
  for kind in model_dict_by_kind:
    try:
      json_data_by_kind[kind] = json.dumps(model_dict_by_kind[kind])
    except TypeError, e:
      logging.exception(e)
      raise e
  return json_data_by_kind


def ArchiveUnpack(file_dict, include_fields=None, exclude_fields=None):
  """Un-archives a list of entities from a dictionary of JSON strings.

  This method undoes the process of ArchivePack.

  Args:
    file_dict: A dictionary JSON strings representing the list of JSON encoded
        files.
    include_fields: A list of attributes for the entities that should be
        extracted from the serialized form.
    exclude_fields: A list of attributes for the entities that should NOT be
        extracted from the serialized form.
  
  Returns:
    A list of models.

  Raises:
    TypeError: Exception raised is the json library is unable to deal with one
        of the types in the models.  The error is logges and re-raised.
  """
  model_list = list()
  for model_kind in file_dict:
    model_class = model.GetClassByKind(model_kind)
    json_data = file_dict[model_kind]
    try:
      model_dict_list = json.loads(json_data)
    except TypeError, e:
      logging.exception(e)
      raise e
    for model_dict in model_dict_list:
      model_instance = model_class()
      #TODO(mdw) this is failing and you have no unittests.
      model_list.append(util.ConvertFromDict(model_instance, model_dict,
          include_fields, exclude_fields))
  return model_list


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
    logging.exception('likely missing the zlib module: %s', e)
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
      logging.exception('likely FakeFile is closed or \'r\': %s', e)
      raise e
  archive_file.close()
  archive_stream.seek(0)
  return archive_stream.getvalue()


def ArchiveDecompress(archive):
  """Decompresses a zip file to an archive dictionary.

  This method undoes the process of ArchiveCompress.

  Args:
    archive: A zipfile of the files represented in the archive dictionary.
  
  Returns:
    A dictionary of strings representing the contents of the files.

  Raises:
    RuntimeError: Exception raised if the zlib module is missing or if the
        StringIO stream is closed unexpectedly.
  """
  archive_stream = StringIO.StringIO(archive)
  try:
    archive_file = zipfile.ZipFile(file=archive_stream,
      compression=zipfile.ZIP_DEFLATED, mode='r')
  except RuntimeError, e:
    logging.exception('likely missing the zlib module: %s', e)
    raise e
  info_list = archive_file.infolist()
  file_dict = dict()
  for info in info_list:
    if '/' in info.filename:
      model_kind = info.filename.split('/')[-1]
    else:
      model_kind = info.filename
    try:
      file_dict[model_kind] = archive_file.read(info.filename)
    except RuntimeError, e:
      logging.exception('likely FakeFile is closed or \'w\': %s', e)
      raise e
  return file_dict


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

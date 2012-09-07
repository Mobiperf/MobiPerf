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

"""Request handlers and some functions to deal with data archival.

For now the only data to be archived is the measurement data.  This data is to
be serialized, compressed and returned to the user in the form of a file 
download or posted to BigStore (Google Storage for Developers).  

Configuration for the storage location is in the global configuration file, and
care should be taken to ensure that the data is sent to the right place and
that the proper ACL has been established.

Control for accessing these methods is set in the /server/app.yaml file and
should be limited to those with administrative access to the datastore.  Since
these methods will likely result in calls that will exceed the response time
limitations for frontend instances, these requests should be handled by
backend instances.

  Archive: A webapp.RequestHandler subclass for dealing with archive requests.
"""
from __future__ import with_statement

__author__ = 'gavaletz@google.com (Eric Gavaletz)'

import datetime

from django.utils import simplejson as json

from google.appengine.api import files
from google.appengine.ext import webapp
from google.appengine.runtime import DeadlineExceededError

from gspeedometer import config
from gspeedometer import config_private
from gspeedometer import model
from gspeedometer.helpers import archive_util
from gspeedometer.helpers import util

import logging


def GetMeasurementDictList(device_id, start=None, end=None, anonymize=False,
                           limit=config.QUERY_FETCH_LIMIT):
  """Retrieves device measurements from the datastore.

  This is factored out to allow for future growth and diversification is what
  data is retrieved and how it is filtered.

  Args:
    device_id: A string that matches a single device.
    start: A dataetime object for the earliest measurement.
    end: A dataetime object for the latest measurement.
    anonymize: A boolean, will anonymize data if set to True.
    limit: An int specifying number of records to fetch.

  Returns:
    A list of dictionary items representing the measurement entities from the
    datastore.

  Raises:
     No exceptions handled here.
     No new exceptions generated here.
  """
  exclude_fields = None
  include_fields = None
  location_precision = None
  if anonymize:
    # set up include/exclude fields for anonymization.
    exclude_fields = config.ANONYMIZE_FIELDS
    location_precision = config.ANONYMIZE_LOCATION_PRECISION

  measurement_q = model.Measurement.all()
  if device_id:
    measurement_q.filter('device_id =', device_id)
  if start:
    measurement_q.filter('timestamp >=', start)
  if end:
    measurement_q.filter('timestamp <', end)
  measurement_q.order('timestamp')
  measurement_list = measurement_q.fetch(config.QUERY_FETCH_LIMIT)
  # NOTE: this is inefficient and should iterate over a query instead of a list.
  # There is a TODO for this in util.MeasurementListToDictList().
  return util.MeasurementListToDictList(measurement_list, include_fields,
                                         exclude_fields, location_precision)


def ParametersToFileNameBase(device_id=None, start_time=None, end_time=None):
  """Builds a file name base based on query parameters.

  This method builds a file name base (lacking an extension, timestamp or
  other things that may be useful to add) that describes the contents of the
  file of directory of files.

  Args:
    device_id: A string key for a device in the datastore.
    start_time: A string with the timestamp for the earliest measurement
        (microseconds UTC).
    end_time: A string with the timestamp for the latest measurement
        (microseconds UTC).

  Returns:
    A name that is suitable for describing the contents of the file based on
        the parameters for the request.

  Raises:
     No exceptions handled here.
     No new exceptions generated here.
  """
  archive_dir = ''
  if start_time:
    archive_dir += 'S-%s' % start_time
  if end_time:
    if len(archive_dir):
      archive_dir += '_'
    archive_dir += 'E-%s' % end_time
  if device_id:
    if len(archive_dir):
      archive_dir += '_'
    archive_dir += 'I-%s' % device_id
  return archive_dir


class Archive(webapp.RequestHandler):
  """A webapp.RequestHandler subclass for dealing with archive requests.

  These request handlers take data from the datastore and convert it to a file
  that can be returned to the user or stored in BigStore.
  """

  def _Archive(self, **unused_args):
    """Processes parameters and packages the data into a file-like object.

    This method does the bulk of the heavy lifting.  It makes sense of the
    request parameters, serializes the data, generates a name used to
    encapsulate the result, and makes the call to compress the file-like
    object.

    URL Args:
      device_id: A string key for a device in the datastore.
      start_time: The timestamp for the earliest measurement (microseconds UTC).
      end_time: The timestamp for the latest measurement (microseconds UTC).

    All of the URL Args are optional and in the case where none are given no
    restrictions are assumed and all the data is returned.  Since these
    handlers should never be called by users it should be understood that this
    case should be avoided.

    Returns:
      A name that is suitable for describing the contents of the file based on
          the parameters for the request, and a file-like object containing the
          measurement data.

    Raises:
       No exceptions handled here.
       No new exceptions generated here.
    """
    #TODO(mdw) Unit test needed.
    # Make sense of parameters
    device_id = self.request.get('device_id')
    start_time = self.request.get('start_time')
    end_time = self.request.get('end_time')
    anonymize = self.request.get('anonymize')
    limit = self.request.get('limit')
    if start_time:
      start = util.MicrosecondsSinceEpochToTime(int(start_time))
    else:
      d0 = datetime.datetime.today() - datetime.timedelta(days=1)
      start = datetime.datetime(d0.year, d0.month, d0.day, 0, 0, 0, 0)
    if end_time:
      end = util.MicrosecondsSinceEpochToTime(int(end_time))
    else:
      d0 = datetime.datetime.today() - datetime.timedelta(days=1)
      end = datetime.datetime(d0.year, d0.month, d0.day, 23, 59, 59, 999999)
    anonymize = not not anonymize
    # Use limit if specified, otherwise use default limit
    if limit is None:
      limit = int(limit)
    else:
      limit = config.QUERY_FETCH_LIMIT_LARGE

    # Get data based on parameters.
    model_list = GetMeasurementDictList(device_id, start, end, anonymize, limit)

    # Serialize the data.
    data = {'Measurement': json.dumps(model_list)}

    # Generate directory/file name based on parameters.
    archive_dir = ParametersToFileNameBase(device_id, start, end)

    # For some reason there was a problem with Unicode chars in the request.
    archive_dir = archive_dir.encode('ascii', 'ignore')

    #NOTE: This is a multiple return.
    return archive_dir, archive_util.ArchiveCompress(data,
        directory=archive_dir)

  def ArchiveToFile(self, **unused_args):
    """Responds with data in compressed JSON format for download.
    
    Allows a file containing the requested data to be downloaded by the user.
    Please see _Archive for details on how arguments are handled and data is
    packaged.

    Raises:
       DeadlineExceededError: Handled in the case where a request exceeds the
          response time limit.  A error is displayed in the VERY short time for
          reporting errors.
       No new exceptions generated here.
    """
    #TODO(mdw) Unit test needed.
    try:
      archive_dir, archive_data = self._Archive()
      self.response.headers['Content-Type'] = config.ARCHIVE_CONTENT_TYPE
      self.response.headers['Content-Disposition'] = (
          config.ARCHIVE_CONTENT_DISPOSITION_BASE % archive_dir)
      self.response.out.write(archive_data)
    except DeadlineExceededError, e:
      logging.exception(e)
      self.response.clear()
      self.response.set_status(500)
      #NOTE: if you see this error make sure it is run on a backend instance.
      self.response.headers['Content-Type'] = 'application/json'
      self.response.out.write(('{\'status\':500,  \'error_name\':\'%s\', '
          '\'error_value\':\'%s\'}' % ('DeadlineExceededError', e)))

    #TODO(gavaletz) log the archive request
    # Consider saving the file to GS too so that it can be returned from there
    # if requested again in the future.

  def ArchiveToGoogleStorage(self, **unused_args):
    """Posts data in compressed JSON format to Google Storage for Developers.
    
    Allows a file containing the requested data to be stored in Google Storage
    for developers for later download.  Please see _Archive for details on
    how arguments are handled and data is packaged.

    In preparation for use of this method it is important that the bucket and
    account that will be used be properly prepared by following these
    instructions (http://goo.gl/S0LRl) paying careful attention to the ACLs.
    This information should be used to adjust the pertinent parts of this
    application's config file.

    Raises:
       DeadlineExceededError: Handled in the case where a request exceeds the
          response time limit.  A error is displayed in the VERY short time for
          reporting errors.
       No new exceptions generated here.
    """
    #TODO(mdw) Unit test needed.
    try:
      archive_dir, archive_data = self._Archive()

      anonymize = self.request.get('anonymize')
      anonymize = not not anonymize

      # Create the file
      if not anonymize:
        bucket = config_private.ARCHIVE_GS_BUCKET
        acl = config_private.ARCHIVE_GS_ACL
      else:
        bucket = config.ARCHIVE_GS_BUCKET_PUBLIC
        acl = config.ARCHIVE_GS_ACL_PUBLIC

      gs_archive_name = '/gs/%s/%s.zip' % (bucket, archive_dir)
      gs_archive = files.gs.create(gs_archive_name,
          mime_type=config.ARCHIVE_CONTENT_TYPE, acl=acl,
          content_disposition=(
              config.ARCHIVE_CONTENT_DISPOSITION_BASE % archive_dir))

      # Open the file and write the data.
      with files.open(gs_archive, 'a') as f:
        f.write(archive_data)

      # Finalize (a special close) the file.
      files.finalize(gs_archive)
      self.response.headers['Content-Type'] = 'application/json'
      self.response.out.write('{\'status\':200,  \'archive_name\':\'%s\'}' %
          gs_archive_name)
    except DeadlineExceededError, e:
      logging.exception(e)
      self.response.clear()
      self.response.set_status(500)
      #NOTE: if you see this error make sure it is run on a backend instance.
      self.response.headers['Content-Type'] = 'application/json'
      self.response.out.write(('{\'status\':500,  \'error_name\':\'%s\', '
          '\'error_value\':\'%s\'}' % ('DeadlineExceededError', e)))

    #TODO(gavaletz) create a datastore entry for the archive params, md5, etc.
    # location might be a gs bucket, someone who downloaded it etc.
    # with this data we do not have to do things twice.

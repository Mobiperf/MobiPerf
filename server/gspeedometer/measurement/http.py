# Copyright (c) 2012, University of Washington
# All rights reserved.
#
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

"""Contains HTTP-validation logic."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging
from gspeedometer.measurement.measurement_wrapper import MeasurementWrapper

class HTTP(MeasurementWrapper):
  """Encapsulates HTTP data and provides methods for analyzing it."""
  vals = dict()
  params = dict()

  def __init__(self, params, values):
    """Initializes the HTTP lookup object with data."""
    self.vals = values

    self.vals['url'] = params['url']

  def GetHTML(self):
    """Returns an HTML representation of this measurement."""
    output = ""
    for key, value in sorted(self.vals.items()):
      output += str(key) + ": " + str(value) + " <br>\n"

    return output

  def Validate(self):
    """ 
      Parses data and returns a dict with validation results.
        valid -> boolean: true if data is good
        error_types -> list: list of errors found
    """
    results = dict()
    results["valid"] = True
    results["error_types"] = []

    # Validation rules: 
    # 0) Proper fields exist
    fields = ['code', 'url']
    optional_fields = ['body', 'body_len', 'headers', 'headers_len', 'time_ms']
    for field in fields:
      if not self.vals.has_key(field):
        results["valid"] = False
        results["error_types"].append("missing_field_" + field)

    try:
      if int(self.vals['code']) == 200:
        for field in optional_fields:
          if not self.vals.has_key(field):
            results["valid"] = False
            results["error_types"].append("missing_field_" + field)


      # 1) URL starts with HTTP
      if not self.vals['url'].startswith("http"):
        results["valid"] = False
        results["error_types"].append("non_http_url")

      # 2) Code was a float between 100 and 600
      if float(self.vals['code']) < 100 or float(self.vals['code']) >= 600:
        results["valid"] = False
        results["error_types"].append("http_code_invalid")

      # 3) if code was between 200 and 300, success, so look at other fields
      if int(self.vals['code']) == 200:
        # 3a) body is non empty
        if len(self.vals['body']) == 0:
          results["valid"] = False
          results["error_types"].append("zero_length_body")

        # 3b) body_len > 0 
        if int(self.vals['body_len']) <= 0:
          results["valid"] = False
          results["error_types"].append("zero_length_body")
          # 3c) headers.length = header_len
        if int(self.vals['header_len']) != len(self.vals['header']):
          results["valid"] = False
          results["error_types"].append("header_length_mismatch")
          # 3d) time_ms > 0 and time_ms < 100000 (expect some outliers...)
        if int(self.vals['time_ms']) < 0 or int(self.vals['time_ms']) > 100000:
          results["valid"] = False
          results["error_types"].append("fetch_time_invalid")

    except KeyError:
      logging.debug("Missing key for http!")

    return results

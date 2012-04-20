# Copyright 2012 University of Washington.
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
#

""" Contains DNS-validation logic."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging
import ipaddr

class DNSLookup:
  """ Encapsulates ping data and provides methods for anlyzing it """
  vals = dict()
  
  def __init__(self, params, values):
    """ Initializes the dns lookup object with data """
    self.vals = values
      
  def PrintData(self):
    """ Prints the dns data for HTML """
    output = ""
    for key, value in sorted(self.vals.items()):      
      output += str(key) + ": " + str(value) + " <br>\n"
    return output
  
  def Validate(self):
    """ 
      Parses data and returns a dict with validation results 
        valid -> boolean: true if data is good
        error_types -> list: list of errors found
    """
    results = dict()
    results["valid"] = True
    results["error_types"] = []    
    
    # Validation rules: 
    # 0) Proper fields exist
    fields = ['address', 'real_hostname', 'time_ms']
    for field in fields:
      if not self.vals.has_key(field):
        results["valid"] = False
        results["error_types"].append("missing_field_" + field)
    try:
    # 1) Target is an IP 
      try:
        foo = ipaddr.IPAddress(self.vals['address'].strip('"'))
      except:
        results["valid"] = False
        results["error_types"].append("address_not_valid")
         
      # 2) lookup time is valid
      # TODO what is the default DNS timeout?
      if float(self.vals['time_ms']) < 0 or float(self.vals['time_ms']) > 15000:
        results["valid"] = False
        results["error_types"].append("lookup_duration_invalid")
         
      # Can't think of a good test for real_hostname    
    except KeyError:
      logging.info("Missing key for dns!")
      
    return results
    
    

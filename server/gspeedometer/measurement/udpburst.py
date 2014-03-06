#Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.
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

__author__ = 'hyyao@umich.edu (Hongyi Yao)'
# I think it was actually originally Haokun that wrote this...


import logging
import ipaddr
from gspeedometer.measurement.measurement_wrapper import MeasurementWrapper

class UDPBurst(MeasurementWrapper):
  """Encapsulates UDPBurst data and provides methods for analyzing it."""
  vals = dict()
 
  def __init__(self, params, values):
    """ Initializes the RRC object """
    self.vals = values

  def GetHTML(self):
    """Returns an HTML representation of this measurement."""
    output = ""
    for key, value in sorted(self.vals.items()):
      output += str(key) + ": " + str(value) + " <br>\n"
    return output

  # TODO do this properly
  def Validate(self):
    """ 
      Parses data and returns a dict with validation results.
        valid -> boolean: true if data is good
        error_types -> list: list of errors found
    """
    results = dict()
    results["valid"] = True
    results["error_types"] = []

    return results

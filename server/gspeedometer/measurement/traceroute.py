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

""" Contains traceroute-validation logic."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

from gspeedometer.measurement.measurement_wrapper import MeasurementWrapper
import ipaddr

class Traceroute(MeasurementWrapper):
  """Encapsulates traceroute data and provides methods for analyzing it."""
  hops = dict()
  rtts = dict()

  def __init__(self, params, values):
    """Initializes the traceroute object with hops and latencies."""
    for key, value in values.items():
      if 'addr' in key or 'rtt' in key:
        parts = key.split("_")
        if parts[2] == 'addr':
          if not self.hops.has_key(parts[1]):
            self.hops[int(parts[1])] = dict()

          self.hops[int(parts[1])][parts[3]] = value
        else:
          if not self.rtts.has_key(parts[1]):
            self.rtts[int(parts[1])] = value

  def GetHTML(self):
    """Returns an HTML representation of this measurement."""
    output = ""
    for key, value in sorted(self.hops.items()):
      output += "Hop " + str(key) + ": "
      for innerkey, innervalue in value.items():
        output += innervalue.strip('"') + " "
      output += self.rtts[key].strip('"') + " <br>\n"
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
    # 1) No missing hops
    # 2) Latencies are greater than zero and less than 3000
    # 3) Hops are IPs
    # 4) Fewer than 45 hops

    last_hop = -1
    for key, value in sorted(self.hops.items()):
      last_hop += 1

      # Test no missing hops
      if last_hop != key:
        results["valid"] = False
        results["error_types"].append("missing_hop")

      # Test reasonable number of hops
      if key > 45:
        results["valid"] = False
        results["error_types"].append("too_many_hops")

      for innerkey, innervalue in value.items():
        # Test valid IPs
        try:
          ipaddr.IPAddress(innervalue.strip('"'))
        except:
          results["valid"] = False
          results["error_types"].append("address_not_valid")

        rtt = float(self.rtts[key].strip('"'))

        # Test latency values
        if rtt < 0 or rtt > 3000:
            results["valid"] = False
            results["error_types"].append("latency_not_valid")

    return results

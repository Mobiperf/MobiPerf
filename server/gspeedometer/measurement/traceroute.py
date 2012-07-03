'''
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
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON 
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
'''

""" Contains traceroute-validation logic."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

from gspeedometer.measurement.measurement_wrapper import MeasurementWrapper
import ipaddr

class Traceroute(MeasurementWrapper):
  """ Encapsulates traceroute data and provides methods for analyzing it """
  hops = dict()
  rtts = dict()

  def __init__(self, params, values):
    """ Initializes the traceroute object with hops and latencies """
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

  def PrintData(self):
    """ Prints the ordered traceroutes for HTML """
    output = ""
    for key, value in sorted(self.hops.items()):
      output += "Hop " + str(key) + ": "
      for innerkey, innervalue in value.items():
        output += innervalue.strip('"') + " "
      output += self.rtts[key].strip('"') + " <br>\n"
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


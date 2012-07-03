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

""" Contains ping-validation logic."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging
import ipaddr
from gspeedometer.measurement.measurement_wrapper import MeasurementWrapper

class Ping(MeasurementWrapper):
  """ Encapsulates ping data and provides methods for analyzing it """
  vals = dict()

  def __init__(self, params, values):
    """ Initializes the ping object with hops and latencies """
    self.vals = values

  def PrintData(self):
    """ Prints the ping data for HTML """
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
    fields = ['mean_rtt_ms', 'min_rtt_ms', 'max_rtt_ms', 'packet_loss',
              'stddev_rtt_ms', 'packets_sent', 'target_ip']
    for field in fields:
      if not self.vals.has_key(field):
        results["valid"] = False
        results["error_types"].append("missing_field_" + field)
    try:
      # 1) Target is an IP 
      try:
        not_used = ipaddr.IPAddress(self.vals['target_ip'].strip('"'))
      except:
        results["valid"] = False
        results["error_types"].append("address_not_valid")

      # 2) Min <= avg <= max RTT
      if float(self.vals['mean_rtt_ms']) < 0 or \
        float(self.vals['mean_rtt_ms']) > 3000:
        results["valid"] = False
        results["error_types"].append("mean_rtt_value_invalid")

      if float(self.vals['min_rtt_ms']) > float(self.vals['mean_rtt_ms']) or \
      float(self.vals['mean_rtt_ms']) > float(self.vals['max_rtt_ms']):
        results["valid"] = False
        results["error_types"].append("rtt_range_invalid")

      # 3) 0 <= loss <= 1
      if float(self.vals['packet_loss']) < 0 or \
        float(self.vals['packet_loss']) > 1:
        results["valid"] = False
        results["error_types"].append("pkt_loss_invalid")

      # 4) stddev >= 0
      if float(self.vals['stddev_rtt_ms']) < 0:
        results["valid"] = False
        results["error_types"].append("stddev_rtt_invalid")

      # 5) max RTT < 3000
      if float(self.vals['max_rtt_ms']) > 3000:
        results["valid"] = False
        results["error_types"].append("max_rtt_too_large")

      # 6) packets sent > 0
      if float(self.vals['packets_sent']) <= 0:
        results["valid"] = False
        results["error_types"].append("packets_sent_invalid")
    except KeyError:
      logging.info("Missing key for ping!")

    return results



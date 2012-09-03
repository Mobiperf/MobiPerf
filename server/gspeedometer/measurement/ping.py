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

"""Contains ping-validation logic."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import logging
import ipaddr
from gspeedometer.measurement.measurement_wrapper import MeasurementWrapper

class Ping(MeasurementWrapper):
  """Encapsulates ping data and provides methods for analyzing it."""
  vals = dict()

  def __init__(self, params, values):
    """ Initializes the ping object with hops and latencies """
    self.vals = values

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

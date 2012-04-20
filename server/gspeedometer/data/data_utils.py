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

""" Contains data-validation utilities."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import socket

class DataUtils(object):
  """ Provides data manipulation utilities """
  @staticmethod
  def IsIpAddress(maybe_ip):
    """ Returns true if the string is an IP """
    try:
        socket.inet_aton(maybe_ip)
        return True
    except socket.error:
      return False
    

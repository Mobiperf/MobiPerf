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

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

class MeasurementWrapper(object):
  """
  This generic class represents the function and fields that a measurement-
  specific wrapper needs to implement. Note this is due to the fact that the 
  Measurement object in model.py is generic and intentionally does not 
  know how to manage operations such as validation and human-readable 
  descriptions.
  """
  vals = dict() # the data for this measurement

  def GetHTML(self):
    """Returns an HTML representation of this measurement."""
    raise NotImplementedError()

  def Validate(self):
    """ 
      Parses data and returns a dict with validation results.
        valid -> boolean: true if data is good
        error_types -> list: list of errors found
    """
    raise NotImplementedError()

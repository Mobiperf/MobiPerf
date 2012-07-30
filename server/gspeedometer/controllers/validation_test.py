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
from gspeedometer import model

"""Tests for controllers/validation.py."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import unittest2

from gspeedometer.controllers import validation, measurement
from gspeedometer.helpers import util
from gspeedometer.controllers.validation import MeasurementValidatorFactory


class ValidationTest(unittest2.TestCase):
  """Tests for controllers/validation.py."""

  START_TIME = "2012-03-21T00:00:00Z"
  END_TIME = "2012-03-22T00:00:00Z"
  LIMIT = 100

  def testValidationOperation(self):
    """Test that validation logic runs without error."""

    validator = validation.Validation()

    # check that each measurement validator works
    for mtype, name in measurement.MEASUREMENT_TYPES:
      query = model.Measurement.all()
      query.filter('timestamp >=', self.START_TIME)
      query.filter('timestamp <=', self.END_TIME)
      query.filter('type == ', mtype)
      results = query.fetch(self.LIMIT)
      for result in results:
        meas_validator = None
        try:
          meas_validator = MeasurementValidatorFactory.CreateValidator(result)
        except RuntimeError:
          self.fail("No validator for %s" % mtype)
          continue
        validation_result = meas_validator.Validate()
        self.assertTrue(validation_result.has_key('valid'),
                        "No key for 'valid' in validation result for %s" % mtype)
        self.assertTrue(validation_result.has_key('error_types'),
            "No key for 'error_types' in validation result for %s" % mtype)
        if validation_result['valid']:
          self.assertEquals(len(validation_result['error_types']), 0,
                       "Valid measurement but nonzero errors")
        else:
          self.assertGreater(len(validation_result['error_types']), 0,
                       "Invalid measurement but zero error details")

    # perform validation task on some data, check that results makes sense
    validator._DoValidation(self.START_TIME, self.END_TIME, self.LIMIT)
    # nonzero number of results
    self.assertGreater(len(validator.type_to_summary), 0,
                       "No data from validation")

    # if there were problems, there better be summaries
    for mtype, data in validator.type_to_summary.items():
      if data.error_count > 0:
        self.assertEquals(data.error_count,
                          len(validator.type_to_details[mtype]),
                          "Mismatch in error count")
      else:
        self.assertFalse(validator.type_to_details.has_key(mtype),
                         "No errors but entries are in type_to_details")

    # TODO(drc): check that data was written to ValidationSummary and 
    # ValidationEntry entities
    query = model.ValidationEntry.all()
    all_measurements = \
      [detail.measurement for detail in validator.type_to_details.values()]
    all_summaries = \
      [summary for summary in validator.type_to_summary.values()]
    for result in query.fetch(self.LIMIT):
      # check that data item is present and summary is present in results
      self.assertDictContainsSubset(result.measurement, all_measurements,
                                    "Measurement not written to datastore")
      self.assertDictContainsSubset(result.summary, all_summaries,
                                    "Measurement summary not present")


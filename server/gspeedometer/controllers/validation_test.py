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

"""Tests for controllers/validation.py."""

__author__ = 'drchoffnes@gmail.com (David Choffnes)'

import unittest2
from gspeedometer import model
from gspeedometer.controllers import validation, measurement
from gspeedometer.helpers import util
from gspeedometer.controllers.validation import MeasurementValidatorFactory


class ValidationTest(unittest2.TestCase):
  """Tests for controllers/validation.py."""

  START_TIME = '2012-03-21T00:00:00Z'
  END_TIME = '2012-03-22T00:00:00Z'
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
          self.fail('No validator for %s' % mtype)
          continue
        validation_result = meas_validator.Validate()
        self.assertTrue(validation_result.has_key('valid'),
            'No key for 'valid' in validation result for %s' % mtype)
        self.assertTrue(validation_result.has_key('error_types'),
            'No key for 'error_types' in validation result for %s' % mtype)
        if validation_result['valid']:
          self.assertEquals(len(validation_result['error_types']), 0,
                       'Valid measurement but nonzero errors')
        else:
          self.assertGreater(len(validation_result['error_types']), 0,
                       'Invalid measurement but zero error details')

    # perform validation task on some data, check that results makes sense
    validator._DoValidation(self.START_TIME, self.END_TIME, self.LIMIT)
    # nonzero number of results
    self.assertGreater(len(validator.type_to_summary), 0,
                       'No data from validation')

    # if there were problems, there better be summaries
    for mtype, data in validator.type_to_summary.items():
      if data.error_count > 0:
        self.assertEquals(data.error_count,
                          len(validator.type_to_details[mtype]),
                          'Mismatch in error count')
      else:
        self.assertFalse(validator.type_to_details.has_key(mtype),
                         'No errors but entries are in type_to_details')

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
                                    'Measurement not written to datastore')
      self.assertDictContainsSubset(result.summary, all_summaries,
                                    'Measurement summary not present')


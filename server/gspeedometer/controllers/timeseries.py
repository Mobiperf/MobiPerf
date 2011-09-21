#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Timeseries plotting of measurement results."""

__author__ = 'mdw@google.com (Matt Welsh)'

import datetime
import logging
import random

from django import forms
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer import model
from gspeedometer.controllers import measurement
from gspeedometer.helpers import util


class Timeseries(webapp.RequestHandler):
  """Controller for the timeseries view."""

  def Timeseries(self, **unused_args):
    """Main handler for the timeseries view."""
    device_id = self.request.get('device_id')
    device = model.DeviceInfo.GetDeviceWithAcl(device_id)

    if self.request.get('measurement'):
      measurement_type = self.request.get('measurement')
    else:
      measurement_type = config.DEFAULT_MEASUREMENT_TYPE_FOR_VIEWING

    now = datetime.datetime.utcnow()
    end_date = datetime.date(now.year, now.month, now.day)
    end_date = end_date + datetime.timedelta(days=1)
    start_date = end_date - datetime.timedelta(days=7)

    measurements = model.Measurement.GetMeasurementListWithAcl(
        None, device_id, start_date, end_date)

    colname = '%s (device %s)' % (measurement_type, device_id)

    tsdata = []
    for meas in measurements:
      # TODO(mdw): This needs to be generalized for different measurement types
      val = '[new Date(%d), %d]' % (
          util.TimeToMicrosecondsSinceEpoch(meas.timestamp) / 1000,
          float(meas.mval_mean_rtt_ms))
      tsdata.append(val)

    template_args = {
        'timeseries_columns': [colname],
        'timeseries_rows': tsdata,
    }
    self.response.out.write(template.render(
        'templates/timeseriesview.html', template_args))

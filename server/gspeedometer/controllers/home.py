#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service to collect and visualize mobile network performance data."""

__author__ = 'mdw@google.com (Matt Welsh)'

from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from gspeedometer import config
from gspeedometer import model


class Home(webapp.RequestHandler):
  """Controller for the home page."""

  def Dashboard(self, **unused_args):
    """Main handler for the service dashboard."""
    errormsg = None

    devices = model.DeviceInfo.all()
    measurements = model.Measurement.all()
    measurements.order('-timestamp')
    measurements = measurements.fetch(config.NUM_MEASUREMENTS_IN_LIST)
    schedule = model.Task.all()
    schedule.order('-created')

    template_args = {
        'devices': devices,
        'measurements': measurements,
        'schedule': schedule,
        'user': users.get_current_user().email(),
        'logout_link': users.create_logout_url('/'),
        'error': errormsg
    }
    self.response.out.write(template.render(
        'templates/home.html', template_args))

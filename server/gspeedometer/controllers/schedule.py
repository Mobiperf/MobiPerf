#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Controller to manage manipulation of measurement schedules."""

__author__ = 'mdw@google.com (Matt Welsh)'

import datetime
import logging

from django import forms
from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import model
from gspeedometer.controllers import measurement


class AddToScheduleForm(forms.Form):
  type = forms.ChoiceField(measurement.MEASUREMENT_TYPES)
  param1 = forms.CharField(required=False)
  param2 = forms.CharField(required=False)
  param3 = forms.CharField(required=False)
  count = forms.CharField(required=False)
  tag = forms.CharField(required=False)
  filter = forms.CharField(required=False)


class Schedule(webapp.RequestHandler):
  """Measurement request handler."""

  def Add(self, **unused_args):
    """Add a task to the schedule."""
    if not self.request.POST:
      add_to_schedule_form = AddToScheduleForm()
    else:
      add_to_schedule_form = AddToScheduleForm(self.request.POST)
      add_to_schedule_form.full_clean()
      if add_to_schedule_form.is_valid():
        thetype = add_to_schedule_form.cleaned_data['type']
        param1 = add_to_schedule_form.cleaned_data['param1']
        param2 = add_to_schedule_form.cleaned_data['param2']
        param3 = add_to_schedule_form.cleaned_data['param3']
        tag = add_to_schedule_form.cleaned_data['tag']
        thefilter = add_to_schedule_form.cleaned_data['filter']
        count = add_to_schedule_form.cleaned_data['count']

        logging.info('Got TYPE: ' + thetype)

        task = model.Task()
        task.created = datetime.datetime.utcnow()
        task.user = users.get_current_user()
        task.type = thetype
        task.tag = tag
        task.filter = thefilter
        task.count = int(count)

        # Set up correct type-specific measurement parameters
        if task.type == 'ping':
          task.mparam_target = param1
          task.mparam_ping_timeout_sec = param2
          task.mparam_packet_size_byte = param3
        elif task.type == 'traceroute':
          task.mparam_target = param1
          task.mparam_pings_per_hop= param2
          task.mparam_max_ping_count = param3
        elif task.type == 'http':
          task.mparam_url = param1
          task.mparam_method = param2
          task.mparam_headers = param3
        elif task.type == 'dns_lookup':
          task.mparam_target = param1
          task.mparam_server = param2
        ## TODO(Wenjie):FINISH THIS...

        task.put()

    schedule = model.Task.all()
    schedule.order('-created')

    template_args = {
        'add_form': add_to_schedule_form,
        'schedule': schedule,
        'user': users.get_current_user().email(),
        'logout_link': users.create_logout_url('/')
    }
    self.response.out.write(template.render(
        'templates/schedule.html', template_args))

  def Delete(self, **unused_args):
    """Delete a task from the schedule."""
    errormsg = None
    message = None

    task_id = self.request.get('id')
    task = model.Task.get_by_id(int(task_id))
    if not task:
      errormsg = 'Task %s does not exist' % task_id
    elif task.user != users.get_current_user():
      errormsg = 'You do not have permission to delete task %s' % task_id
    else:
      # TODO(mdw): Do we need to wrap the following in a transaction?
      # First DeviceTasks that refer to this one
      device_tasks = model.DeviceTask.all()
      device_tasks.filter('task =', task)
      for dt in device_tasks:
        dt.delete()

      # Now delete the task itself
      task.delete()
      message = 'Task %s deleted' % task_id

    schedule = model.Task.all()
    schedule.order('-created')

    template_args = {
        'message': message,
        'error': errormsg,
        'schedule': schedule,
        'user': users.get_current_user().email(),
        'logout_link': users.create_logout_url('/')
    }
    self.response.out.write(template.render(
        'templates/schedule.html', template_args))

# Copyright 2012 Google Inc.
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
#!/usr/bin/python2.4
#

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
from gspeedometer.helpers import acl


class AddToScheduleForm(forms.Form):
  """Form to add a task to the schedule."""
  type = forms.ChoiceField(measurement.MEASUREMENT_TYPES)
  param1 = forms.CharField(required=False)
  param2 = forms.CharField(required=False)
  param3 = forms.CharField(required=False)
  count = forms.IntegerField(required=False, initial=-1,
                             min_value=-1, max_value=1000)
  interval = forms.IntegerField(required=True, label='Interval (sec)',
                                min_value=1, initial=600)
  tag = forms.CharField(required=False)
  filter = forms.CharField(required=False)


class Schedule(webapp.RequestHandler):
  """Measurement request handler."""

  def Add(self, **unused_args):
    """Add a task to the schedule."""
    if not acl.UserIsScheduleAdmin():
      self.error(404)
      return

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
        count = add_to_schedule_form.cleaned_data['count'] or -1
        interval = add_to_schedule_form.cleaned_data['interval']

        logging.info('Got TYPE: ' + thetype)

        task = model.Task()
        task.created = datetime.datetime.utcnow()
        task.user = users.get_current_user()
        task.type = thetype
        if tag:
          task.tag = tag
        if thefilter:
          task.filter = thefilter
        task.count = count
        task.interval_sec = float(interval)

        # Set up correct type-specific measurement parameters
        if task.type == 'ping':
          task.mparam_target = param1
          task.mparam_ping_timeout_sec = param2
          task.mparam_packet_size_byte = param3
        elif task.type == 'traceroute':
          task.mparam_target = param1
          task.mparam_pings_per_hop = param2
          task.mparam_max_hop_count = param3
        elif task.type == 'http':
          task.mparam_url = param1
          task.mparam_method = param2
          task.mparam_headers = param3
        elif task.type == 'dns_lookup':
          task.mparam_target = param1
          task.mparam_server = param2
        task.put()

    schedule = model.Task.all()
    schedule.order('-created')

    template_args = {
        'user_schedule_admin': acl.UserIsScheduleAdmin(),
        'add_form': add_to_schedule_form,
        'schedule': schedule,
        'user': users.get_current_user().email(),
        'logout_link': users.create_logout_url('/')
    }
    self.response.out.write(template.render(
        'templates/schedule.html', template_args))

  def Delete(self, **unused_args):
    """Delete a task from the schedule."""
    if not acl.UserIsScheduleAdmin():
      self.error(404)
      return

    errormsg = None
    message = None
    add_to_schedule_form = AddToScheduleForm()

    task_id = self.request.get('id')
    task = model.Task.get_by_id(int(task_id))
    if not task:
      errormsg = 'Task %s does not exist' % task_id
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
        'user_schedule_admin': acl.UserIsScheduleAdmin(),
        'add_form': add_to_schedule_form,
        'message': message,
        'error': errormsg,
        'schedule': schedule,
        'user': users.get_current_user().email(),
        'logout_link': users.create_logout_url('/')
    }
    self.response.out.write(template.render(
        'templates/schedule.html', template_args))

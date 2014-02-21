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

"""Service to collect and visualize mobile network performance data."""

__author__ = 'mdw@google.com (Matt Welsh)'

import datetime
import urllib
import urlparse

from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from gspeedometer import config
from gspeedometer import model
from gspeedometer.helpers import acl


class Home(webapp.RequestHandler):
  """Controller for the home page."""

  def Dashboard(self, **unused_args):
    """Main handler for the service dashboard."""
    errormsg = None

    devices, cursor = self._GetDeviceList(
        cursor=self.request.get('device_cursor'))
    if cursor:
      parsed_url = list(urlparse.urlparse(self.request.url))
      url_query_dict = {'device_cursor': cursor}
      parsed_url[4] = urllib.urlencode(url_query_dict)
      more_devices_link = urlparse.urlunparse(parsed_url)
    else:
      more_devices_link = None

    schedule = model.Task.all()
    schedule.order('-created')

    template_args = {
        'user_schedule_admin': acl.UserIsScheduleAdmin(),
        'devices': devices,
        'schedule': schedule,
        'user': users.get_current_user().email(),
        'logout_link': users.create_logout_url('/'),
        'more_devices_link': more_devices_link,
        'error': errormsg
    }
    self.response.out.write(template.render(
        'templates/home.html', template_args))

  def _GetDeviceList(self, cursor=None, show_inactive=True):
    device_query = model.DeviceInfo.GetDeviceListWithAcl(cursor=cursor)
    devices = list(device_query.fetch(config.NUM_DEVICES_IN_LIST))

    # Don't need cursor if we are at the end of the list
    if len(devices) == config.NUM_DEVICES_IN_LIST:
      cursor = device_query.cursor()
    else:
      cursor = None

    if not show_inactive:
      active_time = (datetime.datetime.utcnow() -
                     datetime.timedelta(days=config.ACTIVE_DAYS))
      devices = [d for d in devices if d.LastUpdateTime() >= active_time]

    devices.sort(key=lambda dev: dev.LastUpdateTime())
    devices.reverse()

    return (devices, cursor)

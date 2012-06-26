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

"""Service that queries user data and renders on google map."""

__author__ = 'wenjiezeng@google.com (Wenjie Zeng)'

import datetime
import random

from django import forms
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer import model
from gspeedometer.helpers import googlemaphelper, util


# Workaround to mismatch between Google App Engine
# and Django types for MultipleChoiceField inputs
# See http://vanderwijk.info/2011/02/
class SelectMultiple(forms.widgets.SelectMultiple):
  """Fixed SelectMultiple to work with Google App Engine."""

  def value_from_datadict(self, data, unused_files, name):
    try:
      return data.getall(name)
    except:
      return data.get(name, None)


def DeviceChoice(dev):
  """Return a tuple (key, description) for the given device."""
  key_name = dev.key().name()
  description = '%s %s %s' % (
      key_name, dev.manufacturer, dev.model)
  return (key_name, description)


class MapView(webapp.RequestHandler):
  """Controller for the Google map view."""

  def MapView(self, **unused_args):
    """Main handler for the google map view."""
    all_devices = model.DeviceInfo.GetDeviceListWithAcl()
    all_device_ids = [dev.key().name() for dev in all_devices]

    now = datetime.datetime.utcnow()
    init_end_date = datetime.date(now.year, now.month, now.day)
    init_end_date += datetime.timedelta(days=1)
    init_start_date = init_end_date - datetime.timedelta(days=7)

    if self.request.get('device_id'):
      # If map invoked with one device ID, make this one the default
      device_ids = [self.request.get('device_id')]
      # And show most recent data for this device
      start_date = None
      end_date = None
    else:
      device_ids = all_device_ids
      start_date = init_start_date
      end_date = init_end_date

    class FilterMeasurementForm(forms.Form):
      """A form to filter measurement results for a given device."""
      device = forms.MultipleChoiceField(
          widget=SelectMultiple,
          choices=[DeviceChoice(dev) for dev in all_devices],
          required=False)
      start_date = forms.DateField(label='Start date (GMT)')
      end_date = forms.DateField(label='End date (GMT)')

    form_initial = {'start_date': init_start_date, 'end_date': init_end_date}

    if not self.request.POST:
      form = FilterMeasurementForm(initial=form_initial)
    else:
      form = FilterMeasurementForm(self.request.POST, initial=form_initial)
      form.full_clean()
      if form.is_valid():
        device_ids = form.cleaned_data['device'] or device_ids
        start_date = form.cleaned_data['start_date']
        end_date = form.cleaned_data['end_date']

    # Impose a time limit on this to avoid deadline exceeded errors
    timelimit = datetime.datetime.utcnow() + datetime.timedelta(seconds=10)
    measurements = self._GetMeasurements(device_ids, start_date, end_date,
                                         timelimit)

    template_args = {
        'filter_form': form,
        'num_measurements': len(measurements),
        'map_code': self._GetJavascriptCodeForMap(measurements)
    }
    self.response.out.write(template.render(
        'templates/mapview.html', template_args))

  def _GetMeasurements(self, device_ids, start_date=None, end_date=None,
                       timelimit=None):
    """Return a list of measurements.

    Args:
      device_ids: List of device IDs to retrieve measurements for.
      start_date: datetime.datetime object representing start date.
      end_date: datetime.datetime object representing end date.
      timelimit: datetime.datetime object for max time that we should
        keep querying for results.
    Returns:
      A list of measurement objects.
    """
    results = []
    per_device_limit = max(1, int(config.GOOGLEMAP_MARKER_LIMIT /
                                  len(device_ids)))
    for device_id in device_ids:
      if timelimit and datetime.datetime.utcnow() > timelimit:
        break
      subquery = model.Measurement.GetMeasurementListWithAcl(
          limit=per_device_limit,
          device_id=device_id,
          start_time=start_date,
          end_time=end_date)
      results.extend(subquery)

    return results

  def _GetJavascriptCodeForMap(self, measurements):
    """Constructs the javascript code to map measurement results."""
    # TODO(mattp) - This whole thing should be redone as a heatmap
    tmap = googlemaphelper.Map()
    red_icon = googlemaphelper.Icon(icon_id='red_icon',
                                    image='/static/red_location_pin.png')
    green_icon = googlemaphelper.Icon(icon_id='green_icon',
                                      image='/static/green_location_pin.png')

    # Add resource into the google map
    my_key = config.GOOGLEMAP_KEY
    gmap = googlemaphelper.GoogleMapWrapper(key=my_key, themap=tmap)
    gmap.AddIcon(green_icon)
    gmap.AddIcon(red_icon)

    tmap.zoom = config.GOOGLE_MAP_ZOOM
    lat_sum = 0
    lon_sum = 0

    random.seed()
    measurement_cnt = 0
    # Add points to the map

    for meas in measurements:
      prop_entity = meas.device_properties
      # Skip measurements without a location associated with them
      if not prop_entity.location or prop_entity.location_type == 'unknown':
        continue

      device_id = prop_entity.device_info.key().name()
      values = {}
      icon_to_use = red_icon
      # these attributes can be non-existent if the experiment fails
      if meas.success == True:
        # type strings from controller/measurement.py
        if meas.type == 'ping':
          values = {'target': meas.mparam_target,
                    'mean rtt': meas.mval_mean_rtt_ms,
                    'max rtt': meas.mval_max_rtt_ms,
                    'min rtt': meas.mval_min_rtt_ms,
                    'rtt stddev': meas.mval_stddev_rtt_ms,
                    'packet loss': meas.mval_packet_loss}
          if (float(meas.mval_mean_rtt_ms) <
              config.SLOW_PING_THRESHOLD_MS):
            icon_to_use = green_icon

        elif meas.type == 'http':
          values = {'url': meas.mparam_url,
                    'code': meas.mval_code,
                    'time (msec)': meas.mval_time_ms,
                    'header length (bytes)': meas.mval_headers_len,
                    'body length (bytes)': meas.mval_body_len}
          if meas.mval_code == '200':
            icon_to_use = green_icon

        elif meas.type == 'dns_lookup':
          values = {'target': meas.mparam_target,
                    'IP address': meas.mval_address,
                    'real hostname': meas.mval_real_hostname,
                    'time (msec)': meas.mval_time_ms}
          if float(meas.mval_time_ms) < config.SLOW_DNS_THRESHOLD_MS:
            icon_to_use = green_icon

        elif meas.type == 'traceroute':
          values = {'target': meas.mparam_target,
                    '# of hops': meas.mval_num_hops}
          if (float(meas.mval_num_hops) <
              config.LONG_TRACEROUTE_HOP_COUNT_THRESHOLD):
            icon_to_use = green_icon

      htmlstr = self._GetHtmlForMeasurement(device_id, meas, values)
      # Use random offset to deal with overlapping points
      rand_lat = (random.random() - 0.5) * config.LOCATION_FUZZ_FACTOR
      rand_lon = (random.random() - 0.5) * config.LOCATION_FUZZ_FACTOR
      point = (prop_entity.location.lat + rand_lat,
               prop_entity.location.lon + rand_lon,
               htmlstr, icon_to_use.icon_id)
      lat_sum += prop_entity.location.lat
      lon_sum += prop_entity.location.lon
      measurement_cnt += 1
      tmap.AddPoint(point)

    # Set the center of the viewport
    if measurement_cnt:
      center_lat = lat_sum / measurement_cnt
      center_lon = lon_sum / measurement_cnt
      tmap.center = (center_lat, center_lon)
    else:
      tmap.center = config.DEFAULT_MAP_CENTER

    mapcode = gmap.GetGoogleMapScript()

    return mapcode

  def _GetHtmlForMeasurement(self, device_id, meas, values):
    """Returns the HTML string representing a measurement result.

    Args:
      device_id: The device ID
      meas: The measurement object.
      values: The measurement values to include in the table.
    Returns:
      An HTML string.
    """
    result = '<html><body><b>%s</b><br/>' % meas.type
    result += 'Device %s<br/>' % device_id
    result += '%s<br/>' % meas.timestamp
    result += """<style media="screen" type="text/css"></style>"""
    result += """<table style="border:1px #000000 solid;">"""

    for k, v in values.iteritems():
      result += (
          '<tr>'
          '<td align=\"left\" '
          'style=\"padding-right:10px;border-bottom:'
          '1px #000000 dotted;\">%s</td>'
          '<td align=\"left\" '
          'style=\"padding-right:10px;border-bottom:'
          '1px #000000 dotted;\">%s</td>'
          '</tr>' % (k, v))
    result += '</table>'
    if not values:
      result += '<br/>Measurement failed.'
    result += '</body></html>'
    return result

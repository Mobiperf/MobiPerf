#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service that queries user data and renders on google map."""

__author__ = 'wenjiezeng@google.com (Wenjie Zeng)'

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
from gspeedometer.helpers import googlemaphelper



# Stupid workaround to mismatch between Google App Engine
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

    # If map invoked with one device ID, make this one the default
    if self.request.get('device_id'):
      device_ids = [self.request.get('device_id')]
    else:
      device_ids = all_device_ids

    class FilterMeasurementForm(forms.Form):
      """A form to filter measurement results for a given device."""
      device = forms.MultipleChoiceField(
          widget=SelectMultiple,
          choices=[DeviceChoice(dev) for dev in all_devices],
          required=False)
      measurement_type = forms.ChoiceField(measurement.MEASUREMENT_TYPES,
                                           label='Measurement type')
      start_date = forms.DateField(label='Start date (GMT)')
      end_date = forms.DateField(label='End date (GMT)')

    measurement_type = config.DEFAULT_MEASUREMENT_TYPE_FOR_VIEWING
    now = datetime.datetime.utcnow()
    end_date = datetime.date(now.year, now.month, now.day)
    end_date = end_date + datetime.timedelta(days=1)
    start_date = end_date - datetime.timedelta(days=7)

    form_initial = {'start_date': start_date, 'end_date': end_date}

    if not self.request.POST:
      form = FilterMeasurementForm(initial=form_initial)
    else:
      form = FilterMeasurementForm(self.request.POST, initial=form_initial)
      form.full_clean()
      if form.is_valid():
        device_ids = form.cleaned_data['device'] or device_ids
        measurement_type = form.cleaned_data[ 'measurement_type']
        start_date = form.cleaned_data['start_date']
        end_date = form.cleaned_data['end_date']

    measurements = self._GetMeasurements(device_ids,
                                         measurement_type,
                                         start_date,
                                         end_date)

    template_args = {
        'filter_form': form,
        'num_measurements': len(measurements),
        'map_code': self._GetJavascriptCodeForMap(measurements)
    }
    self.response.out.write(template.render(
        'templates/mapview.html', template_args))


  def _GetMeasurements(self, device_ids, measurement_type,
                       start_date, end_date):
    results = []
    for device_id in device_ids:
      subquery = model.Measurement.GetMeasurementListWithAcl(
          config.GOOGLEMAP_MARKER_LIMIT,
          device_id,
          start_date,
          end_date)
      results.extend(subquery)
    return results

  def _GetJavascriptCodeForMap(self, measurements):
    """Constructs the java script code to map measurement resultsp."""
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

    logging.info(str(gmap))

    tmap.zoom = config.GOOGLE_MAP_ZOOM
    lat_sum = 0
    lon_sum = 0

    random.seed()
    measurement_cnt = 0
    # Add points to the map

    for measurement in measurements:
      measurement_cnt += 1
      prop_entity = measurement.device_properties
      values = {}
      icon_to_use = red_icon
      # these attributes can be non-existent if the experiment fails
      if measurement.success == True:
        # type strings from controller/measurement.py
        if measurement.type == 'ping':
          values = {'target': measurement.mparam_target,
                    'mean rtt': measurement.mval_mean_rtt_ms,
                    'max rtt': measurement.mval_max_rtt_ms,
                    'min rtt': measurement.mval_min_rtt_ms,
                    'rtt stddev': measurement.mval_stddev_rtt_ms,
                    'packet loss': measurement.mval_packet_loss}
          if (float(measurement.mval_mean_rtt_ms) <
              config.SLOW_PING_THRESHOLD_MS):
            icon_to_use = green_icon

        elif measurement.type == 'http':
          values = {'url': measurement.mparam_url,
                    'code': measurement.mval_code,
                    'time (msec)': measurement.mval_time_ms,
                    'header length (bytes)': measurement.mval_headers_len,
                    'body length (bytes)': measurement.mval_body_len}
          if measurement.mval_code == '200':
            icon_to_use = green_icon

        elif measurement.type == 'dns_lookup':
          values = {'target': measurement.mparam_target,
                    'IP address': measurement.mval_address,
                    'real hostname': measurement.mval_real_hostname,
                    'time (msec)': measurement.mval_time_ms}
          if float(measurement.mval_time_ms) < config.SLOW_DNS_THRESHOLD_MS:
            icon_to_use = green_icon

        elif measurement.type == 'traceroute':
          values = {'target': measurement.mparam_target,
                    '# of hops': measurement.mval_num_hops}
          if (float(measurement.mval_num_hops) <
              config.LONG_TRACEROUTE_HOP_COUNT_THRESHOLD):
            icon_to_use = green_icon

      htmlstr = self._GetHtmlForMeasurement(measurement.device_id,
                                            measurement.type,
                                            values)
      random_radius = 0.001
      # Use random offset to deal with overlapping points
      rand_lat = (random.random() - 0.5) * random_radius
      rand_lon = (random.random() - 0.5) * random_radius
      point = (prop_entity.location.lat + rand_lat,
               prop_entity.location.lon + rand_lon,
               htmlstr, icon_to_use.icon_id)
      lat_sum += prop_entity.location.lat
      lon_sum += prop_entity.location.lon
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

  def _GetHtmlForMeasurement(self, device_id, meas_type, values):
    """Returns the HTML string representing the Ping result."""
    result = ['<html><body><h4>%s result on device %s</h4><br/>' %
              (meas_type, device_id)]
    result.append("""<style media="screen" type="text/css"></style>""")
    result.append("""<table style="border:1px #000000 solid;">""")

    for k, v in values.iteritems():
      result.append((
          '<tr>'
          '<td align=\"left\" '
          'style=\"padding-right:10px;border-bottom:'
          '1px #000000 dotted;\">%s</td>'
          '<td align=\"left\" '
          'style=\"padding-right:10px;border-bottom:'
          '1px #000000 dotted;\">%s</td>'
          '</tr>' % (k, v)))
    result.append('</table>')
    if not values:
      result.append('<br/>This measurement has failed.')
    resultstr = ''.join(result)
    return resultstr

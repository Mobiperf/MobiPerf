#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service that queries user data and renders on google map."""

__author__ = 'wenjiezeng@google.com (Wenjie Zeng)'

import datetime
import logging
import random

from django import forms
from google.appengine.api import users
from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer import model
from gspeedometer.helpers import googlemaphelper
from gspeedometer.controllers import measurement


class FilterMeasurementForm(forms.Form):
  def __init__(self, *args, **kwargs):
    devices = kwargs.pop('device', [])
    super(FilterMeasurementForm, self).__init__(*args, **kwargs)
    self.fields['device'] = forms.ChoiceField(devices, label='Device ID')

  thetype = forms.ChoiceField(measurement.MEASUREMENT_TYPES,
                              label='Measurement type')
  start_date = forms.DateField(label='Start date (GMT)')
  end_date = forms.DateField(label='End date (GMT)')


class GoogleMapView(webapp.RequestHandler):
  """Controller for the Speedometer google map page."""

  def MapView(self, **unused_args):
    """Main handler for the google map view."""
    thetype = config.DEFAULT_MEASUREMENT_TYPE_FOR_VIEWING

    now = datetime.datetime.utcnow()
    end_date = datetime.date(now.year, now.month, now.day)
    start_date = end_date - datetime.timedelta(days=1)

    form_initial = {'start_date': start_date,
                    'end_date': end_date}
    deviceinfo_list = self._GetDevicesForUser()
    device_key = ''
    if deviceinfo_list:
      device_key = str(deviceinfo_list[0].key())
    logging.info('device_key is %s' % device_key)

    device_choices = [(str(device.key()), device.id)
                      for device in deviceinfo_list]
    logging.info(device_choices)

    if not self.request.POST:
      filter_measurement_form = FilterMeasurementForm(initial=form_initial,
                                                      device=device_choices)
    else:
      filter_measurement_form = FilterMeasurementForm(self.request.POST,
                                                      initial=form_initial,
                                                      device=device_choices)
      filter_measurement_form.full_clean()
      if filter_measurement_form.is_valid():
        thetype = filter_measurement_form.cleaned_data['thetype']
        start_date = filter_measurement_form.cleaned_data['start_date']
        end_date = filter_measurement_form.cleaned_data['end_date']
        device_key = filter_measurement_form.cleaned_data['device']

    measurements = self._GetMeasurementsForUser(thetype,
                                                start_date,
                                                end_date,
                                                device_key)

    logging.debug('start_date=%s, end_date=%s' % (start_date, end_date))

    template_args = {
        'filter_form': filter_measurement_form,
        'map_code': self._GetJavascriptCodeForMap(measurements)
    }
    self.response.out.write(template.render(
        'templates/map.html', template_args))

  def _GetDevicesForUser(self):
    user = users.get_current_user()
    devices = model.DeviceInfo.all()
    devices.filter('user =', user)
    return devices.fetch(limit=config.DEVICE_LIMIT)

  def _GetMeasurementsForUser(self, thetype, start_date, end_date, device_key):
    # start_date and end_date are either initialized by the default value
    # or the POST value
    query = db.GqlQuery('SELECT * FROM Measurement '
                        'WHERE type=:1 AND '
                        'timestamp>=:2 AND '
                        'timestamp<=:3 AND '
                        'ANCESTOR IS :4 '
                        'ORDER BY timestamp DESC',
                        thetype,
                        start_date,
                        end_date,
                        device_key)

    return query.fetch(limit=config.GOOGLEMAP_MARKER_LIMIT)

  def _GetJavascriptCodeForMap(self, measurements):
    """Constructs the java script code to show ping results on google map."""
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
      # these attributes can be non-existant if the experiment fails
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

    # Set the center of the view port
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
    logging.info('generated location pin html is %s', resultstr)
    return resultstr

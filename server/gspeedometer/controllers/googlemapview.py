#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service that queries user data and renders on google map."""

__author__ = 'wenjiezeng@google.com (Wenjie Zeng)'

import logging

import random

from google.appengine.ext import db
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from gspeedometer import config
from gspeedometer.helpers import googlemaphelper


class GoogleMapView(webapp.RequestHandler):
  """Controller for the Speedometer google map page."""

  def MapView(self, **unused_args):
    """Main handler for the google map view."""
    measurements = db.GqlQuery('SELECT * FROM Measurement '
                               'WHERE type=:1 '
                               'ORDER BY timestamp DESC '
                               'LIMIT 40',
                               'ping')
    template_args = {
        'map_code': self._GetJavascriptCodeForPingMap(measurements)
    }
    self.response.out.write(template.render(
        'templates/map.html', template_args))

  def _GetJavascriptCodeForPingMap(self, measurements):
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
      # these attributes can be non-existant if the experiment fails
      if hasattr(measurement, 'mval_mean_rtt_ms'):
        values = {'mean rtt': measurement.mval_mean_rtt_ms,
                  'max rtt': measurement.mval_max_rtt_ms,
                  'min rtt': measurement.mval_min_rtt_ms,
                  'rtt stddev': measurement.mval_stddev_rtt_ms,
                  'packet loss': measurement.mval_packet_loss}

      htmlstr = self.GetHtmlForPing(measurement.device_id,
                                    measurement.mparam_target,
                                    values)
      random_radius = 0.001
      # Use random offset to deal with overlapping points
      rand_lat = (random.random() - 0.5) * random_radius
      rand_lon = (random.random() - 0.5) * random_radius
      if hasattr(measurement, 'mval_mean_rtt_ms') and float(measurement.mval_mean_rtt_ms) < 150:
        point = (prop_entity.location.lat + rand_lat,
                 prop_entity.location.lon + rand_lon,
                 htmlstr, green_icon.icon_id)
      else:
        point = (prop_entity.location.lat + rand_lat,
                 prop_entity.location.lon + rand_lon,
                 htmlstr, red_icon.icon_id)
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

  def GetHtmlForPing(self, device_id, target, values):
    """Returns the HTML string representing the Ping result."""
    result = ['<html><body><h4>Ping result on device %s</h4><br/>' % device_id]
    result.append("""<style media="screen" type="text/css"></style>""")
    result.append("""<table style="border:1px #000000 solid;">""")
    result.append(('<tr>'
                   '<td align=\"left\" '
                   'style=\"padding-right:10px;border-bottom:'
                   '1px #000000 dotted;\">target</td>'
                   '<td align=\"left\" '
                   'style=\"padding-right:10px;border-bottom:'
                   '1px #000000 dotted;\">%s</td>'
                   '</tr>' % target))

    for k, v in values.iteritems():
      result.append((
          '<tr>'
          '<td align=\"left\" '
          'style=\"padding-right:10px;border-bottom:'
          '1px #000000 dotted;\">%s</td>'
          '<td align=\"left\" '
          'style=\"padding-right:10px;border-bottom:'
          '1px #000000 dotted;\">%.3f</td>'
          '</tr>' % (k, float(v))))
    result.append('</table>')
    if (len(values) == 0):
      result.append("<br/>This measurement has failed.");
    resultstr = ''.join(result)
    logging.info('generated location pin html is %s', resultstr)
    return resultstr

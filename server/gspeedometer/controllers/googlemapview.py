#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Service to that queries user data and renders on google map"""

__author__ = 'wenjiezeng@google.com (Matt Welsh)'

import logging

import random

from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template
from google.appengine.ext import db
from google.appengine.ext.db import ReferencePropertyResolveError

from gspeedometer import config
from gspeedometer import model
#from gspeedometer.helpers.googlemaphelper import Icon
#from gspeedometer.helpers.googlemaphelper import Map 
#from gspeedometer.helpers.googlemaphelper import GoogleMapWrapper
from gspeedometer.helpers.pymaps import Icon
from gspeedometer.helpers.pymaps import Map 
from gspeedometer.helpers.pymaps import GoogleMapWrapper


class GoogleMapView(webapp.RequestHandler):
  """Controller for the home page."""

  def MapView(self, **unused_args):
    """Main handler for the google map view."""
    measurements = db.GqlQuery("SELECT * FROM Measurement "
                                "WHERE type = :1 "
                                "ORDER BY timestamp DESC "
                                "LIMIT 30", 
                                "ping")
    template_args = {
        'map_code': self.ShowMap(measurements),
    }
    self.response.out.write(template.render(
        'templates/map.html', template_args))

  def ShowMap(self, measurements):
    # Create a map - pymaps allows multiple maps in an object
    tmap = Map()
    red_icon = Icon('red_icon')
    green_icon = Icon('green_icon')
    
    # Add resource into the google map
    my_key = "ABQIAAAAXVsx51W4RvTDuDUeIpF0qxRM6wioRijWnXUBkeVfSDD8OvINmRSaz2Wa7XNxJDFBqSTkzyC0aVYxYw"
    gmap = GoogleMapWrapper(key=my_key, maplist=[tmap], iconlist=[])
    gmap.AddIcon(green_icon)
    gmap.AddIcon(red_icon)

    tmap.zoom = 15
    lat_sum = 0
    lon_sum = 0

    random.seed()
    measurement_cnt = 0
    # Add points to the map 
    for measurement in measurements:
      measurement_cnt += 1
      prop_entity = measurement.device_properties
      logging.info("ts=%s, lat=%f, lon=%f, ping_mean_rtt=%f", measurement.timestamp, 
                    prop_entity.location.lat, prop_entity.location.lon, 
                    float(measurement.mval_mean_rtt_ms))
      values = {'mean rtt' : measurement.mval_mean_rtt_ms, 
                'max rtt' : measurement.mval_max_rtt_ms, 
                'min rtt' : measurement.mval_min_rtt_ms, 
                'rtt stddev' : measurement.mval_stddev_rtt_ms, 
                'packet loss' : measurement.mval_packet_loss}
      htmlstr = self.GetHtmlForPing(measurement.device_id, measurement.mparam_target, values) 
      random_radius = 0.001
      rand_lat = (random.random() - 0.5) * random_radius
      rand_lon = (random.random() - 0.5) * random_radius
      if float(measurement.mval_mean_rtt_ms) < 150:
        point = (prop_entity.location.lat + rand_lat, prop_entity.location.lon + rand_lon, htmlstr, green_icon.id)
      else:
        point = (prop_entity.location.lat + rand_lat, prop_entity.location.lon + rand_lon, htmlstr, red_icon.id)
      lat_sum += prop_entity.location.lat
      lon_sum += prop_entity.location.lon
      tmap.AddPoint(point)

    # Set the center of the view port
    center_lat = lat_sum / measurement_cnt
    center_lon = lon_sum / measurement_cnt
    tmap.center = (center_lat,center_lon)

    mapcode = gmap.GetGoogleMapScript()

    return mapcode

  def GetHtmlForPing(slef, device_id, target, values):
    # Returns the HTML string representing the Ping result
    result = ["<html><body><h4>Ping result on device %s</h4><br/>" % device_id]
    result.append("""<style media="screen" type="text/css"></style>""")
    result.append("""<table style="border:1px #000000 solid;">""")
    result.append(("<tr>"
                  "<td align=\"left\" "
                  "style=\"padding-right:10px;border-bottom:"
                  "1px #000000 dotted;\">target</td>"
                  "<td align=\"left\" "
                  "style=\"padding-right:10px;border-bottom:"
                  "1px #000000 dotted;\">%s</td>"
                  "</tr>" % target))
    for k, v in values.iteritems():
      result.append((
          "<tr>"
          "<td align=\"left\" "
          "style=\"padding-right:10px;border-bottom:"
          "1px #000000 dotted;\">%s</td>"
          "<td align=\"left\" "
          "style=\"padding-right:10px;border-bottom:"
          "1px #000000 dotted;\">%.3f</td>"
          "</tr>" % (k, float(v))))
    result.append("</table>")
    logging.info("generated location pin html is %s", ''.join(result))
    return result

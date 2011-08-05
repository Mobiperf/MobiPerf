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
from gspeedometer.helpers import pymaps 


class GoogleMapView(webapp.RequestHandler):
  """Controller for the home page."""

  def MapView(self, **unused_args):
    """Main handler for the google map view."""
    measurements = db.GqlQuery("SELECT * FROM Measurement "
                                "WHERE type = :1 "
                                "ORDER BY timestamp DESC "
                                "LIMIT 30", 
                                "ping")
    markers = []
    random.seed()
    for measurement in measurements:
      prop_entity = measurement.device_properties
      random_radius = 0.001
      rand_lat = (random.random() - 0.5) * random_radius
      rand_lon = (random.random() - 0.5) * random_radius
      markers += [{"ts" : measurement.timestamp, "lat" : prop_entity.location.lat + rand_lat, 
          "lon" : prop_entity.location.lon + rand_lon, "rrt" : float(measurement.mval_mean_rtt_ms)}]
      logging.info("ts=%s, lat=%f, lon=%f, ping_mean_rtt=%f", measurement.timestamp, 
                    prop_entity.location.lat, prop_entity.location.lon, 
                    float(measurement.mval_mean_rtt_ms))

    template_args = {
        'map_code': self.ShowMap(markers),
    }
    self.response.out.write(template.render(
        'templates/map.html', template_args))

  def ShowMap(self, measurements):
    # Create a map - pymaps allows multiple maps in an object
    tmap = pymaps.Map(pointlist=[])
    red_icon = pymaps.Icon('red_icon')
    green_icon = pymaps.Icon('green_icon')
    tmap.zoom = 15

    # Convert the coordinates
    center_lon = -122.351
    center_lat = 47.652
    tmap.center = (center_lat,center_lon)

    # Add points to the map 
    for measurement in measurements:
      values = {'mean round trip time' : measurement.mval_mean_rtt
      if marker["rrt"] < 150:
        point = (marker["lat"], marker["lon"], str(marker["rrt"]), green_icon.id)
      else:
        point = (marker["lat"], marker["lon"], str(marker["rrt"]), red_icon.id)
      tmap.setpoint(point)

    # Put your own google key here
    my_key = "ABQIAAAAXVsx51W4RvTDuDUeIpF0qxRM6wioRijWnXUBkeVfSDD8OvINmRSaz2Wa7XNxJDFBqSTkzyC0aVYxYw"
    gmap = pymaps.PyMap(key=my_key, maplist=[tmap], iconlist=[])
    gmap.addicon(green_icon)
    gmap.addicon(red_icon)

    mapcode = gmap.pymapjs()

    return mapcode

  def GetHtmlForPing(slef, measurement, prop, target, values):
    # Returns the HTML string representing the Ping result
    result = "<html><body><h4>Ping result for %s on device %s</h4><br/>" % (target, measurement.device_id)
    result += "<table border=\"1\">"
    result += "<tr><th>Name</th><th>Value</th></tr>"
    for k, v in values:
      result += "<tr><th>%s</th><th>%s</th></tr>" % (k, v)
    result += "</table>"

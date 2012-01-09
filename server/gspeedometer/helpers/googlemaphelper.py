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

"""Wrapper for Google Map Javascript interface."""

__author__ = 'wenjiezeng@google.com (Wenjie Zeng)'

from google.appengine.ext.webapp import template

from gspeedometer import config


class Icon(object):
  """Represent the resource needed to draw a Google Map icon."""

  def __init__(self, icon_id='icon',
               image=config.DEFAULT_GOOGLEMAP_ICON_IMAGE, shadow='',
               icon_size=(15, 16), shadow_size=(18, 20),
               icon_anchor=(7, 16), info_window_anchor=(5, 1)):
    self.icon_id = icon_id
    # TODO(wenjiezeng): May want to check whether the resource exists.
    self.image = image
    self.shadow = shadow
    self.icon_size = icon_size
    self.shadow_size = shadow_size
    self.icon_anchor = icon_anchor
    self.info_window_anchor = info_window_anchor

  def __str__(self):
    return 'Icon <id %s, image %s, iconSize %s>' % (
        self.icon_id, self.image, self.icon_size)


class Map(object):
  """Represents the resource needed to draw a map."""

  def __init__(self, map_id='map', width='500px', height='300px',
               center=(0, 0), zoom='2', show_navcontrols=True,
               show_mapcontrols=True, pointlist=None):
    # id of the html div component
    self.map_id = map_id
    # map div width
    self.width = width
    # map div height
    self.height = height
    # center (lat, long) of the view port
    self.center = center
    # zoom level of the map
    self.zoom = zoom
    # whether to show google map navigation controls
    self.show_navcontrols = show_navcontrols
    # whether to show toogle map type (sat/map/hybrid) controls
    self.show_mapcontrols = show_mapcontrols
    # point list
    self.points = pointlist or []

  def AddPoint(self, point):
    """Add a point to the map."""
    self.points.append(point)

  def __str__(self):
    return 'Map <id %s, width %s, height %s, center %s>' % (
        self.map_id, self.width, self.height, str(self.center))


class GoogleMapWrapper(object):
  """A Python wrapper for Google Maps API."""

  def __init__(self, key=None, themap=None, iconlist=None):
    # Set the appropriate Google Map key of yours
    self.key = key
    self.themap = themap or Map()
    self.icons = iconlist or []

  def AddIcon(self, icon):
    """Add an icon as into the map resource so that points can reference it."""
    self.icons.append(icon)

  def GetGoogleMapScript(self):
    """Returns complete javacript for rendering map."""
    template_args = {
        'googlemap_key': self.key,
        'map': self.themap,
        'points': self._GetPointsScript(self.themap),
        'icons': self.icons,
        'center_lat': self.themap.center[0],
        'center_lon': self.themap.center[1]
    }

    return template.render(
        'templates/googlemaphelper.html', template_args)

  def _GetPointsScript(self, themap):
    if not themap.points:
      return '[]'

    script_list = ['[']

    # Constructs the points for the map
    for point in themap.points:
      script_list.append("[%f, %f, '%s', %s]" % point)
      script_list.append(',')
    script_list = script_list[:-1]
    script_list.append('];\n')
    js = ''.join(script_list)
    js = js.replace("u'", "'")

    return js

  def __str__(self):
    iconlist = []
    for icon in self.icons:
      iconlist.append(str(icon))
    iconstr = ''.join(iconlist)

    return 'GoogleMapWrapper <key %s, icons %s>' % (
        self.key, iconstr)

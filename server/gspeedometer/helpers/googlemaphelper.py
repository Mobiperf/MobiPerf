#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Wrapper for Google Map Javascript interface."""

__author__ = 'wenjiezeng@google.com (Wenjie Zeng)'

from gspeedometer import config

class Icon(object):
  """Represent the resource needed to draw an icon."""
  def __init__(self, id='icon', 
               image=config.DEFAULT_GOOGLEMAP_ICON_IMAGE, shadow='', 
               icon_size=(15, 16), shadow_size=(18, 20),
               icon_anchor=(7, 16), info_window_anchor=(5, 1)):
    self.id = id
    if (os.path.exists(image)):
      self.image = image
    self.shadow = shadow
    self.icon_size = icon_size
    self.shadow_size = shadow_size
    self.icon_anchor = icon_anchor
    self.info_window_anchor = info_window_anchor

  def __str__(self):
    return "Icon <id %s, image %s, iconSize %s>" % (
        self.id, self.image, self.icon_size)

class Map(object):
  """Represents the resource needed to draw a map."""
  def __init__(self, map_id='map', width='500px', height='300px', 
               center=(0, 0), zoom='2', show_navcontrols=True, 
               show_mapcontrols=True, pointlist=[]):
    # id of the html div component
    self.id = map_id
    # map div width
    self.width = width
    # map div height
    self.height = height
    # center (lat, long) of the view port 
    self.center = center
    # zoom level of the map
    self.zoom  = zoom
    # whether to show google map navigation controls
    self.show_navcontrols = show_navcontrols
    # whether to show toogle map type (sat/map/hybrid) controls
    self.show_mapcontrols = show_mapcontrols
    # point list
    self.points = pointlist
    
  def __str__(self):
    return "Map <id %s, width %s, height %s, center %s>" % (
        self.id, self.width, self.height, str(self.center))
    
  def AddPoint(self, point):
    """Add a point to the map."""
    self.points.append(point)


class GoogleMapWrapper:
  """A Python wrapper for Google Maps API."""
  def __init__(self, key=None, maplist=[Map()], iconlist=[]):
    # Set the appropriate Google Map key of yours
    self.key = key 
    self.maps = maplist
    self.icons = iconlist
  
  def __str__(self):
    iconlist = []
    for icon in self.icons:
      iconlist.append(str(icon))
    iconstr = ''.join(iconlist)

    return "GoogleMapWrapper <key %s, icons %s>" % (
        self.key, iconstr)

  def AddIcon(self, icon):
    """Add an icon as into the map resource so that points can reference it."""
    self.icons.append(icon)
      
  def GetGoogleMapScript(self):
    """ Returns complete javacript for rendering map."""
    self.finalscript = ("\n<script src=\"http://maps.google.com/maps?file=api&amp;"
                        "v=2&amp;key=%s\" type=\"text/javascript\"></script>\n"
                        "<script type=\"text/javascript\">\n"
                        "//<![CDATA[\n"
                        "function load() {\n"
                        "  if (GBrowserIsCompatible()) {\n"
                        "    function point(lat, long, html, icon) {\n"
                        "      this.gpoint = new GMarker(new GLatLng(lat, long), icon);\n"
                        "      this.html = html;\n"
                        "    }\n\n"              
                        "    function map(id, points, lat, long, zoom) {\n"
                        "      this.id = id;\n"
                        "      this.points = points;\n"
                        "      this.gmap = new GMap2(document.getElementById(this.id));\n"
                        "      this.gmap.setCenter(new GLatLng(lat, long), zoom);\n"
                        "      this.addMarker = addMarker;\n"
                        "      this.addMarkers = addMarkers;\n"
                        "      this.makePointsFromArray = makePointsFromArray;\n\n"
                        "      function addMarkers(array) {\n"
                        "        for (var i in array) {\n"
                        "          this.addMarker(array[i]);\n"
                        "        }\n"
                        "      }\n\n"
                        "      function makePointsFromArray(map_points) {\n"           
                        "        for (var i in map_points) {\n"
                        "          points[i] = new point(map_points[i][0],\n"
                        "                                map_points[i][1],\n"
                        "                                map_points[i][2],\n"
                        "                                map_points[i][3]);\n"
                        "        }\n"
                        "        return points;\n"
                        "      }\n\n"
                        "      function addMarker(point) {\n"
                        "        if (point.html) {\n"
                        "          GEvent.addListener(point.gpoint, \"click\",\n" 
                        "            function() {\n"
                        "              point.gpoint.openInfoWindowHtml(point.html);\n"
                        "            });\n"
                        "        }\n"
                        "        this.gmap.addOverlay(point.gpoint);\n"
                        "      }\n\n"
                        "      this.points = makePointsFromArray(this.points);\n"
                        "      this.addMarkers(this.points);\n"
                        "    }\n" 
                        "%s\n%s\n"
                        "  }\n"
                        "}\n"
                        "//]]>"
                        "</script>\n"
                        % (self.key, self._GetScriptForIcons(), self._GetScriptForMaps()))
    return self.finalscript
      
  def _GetNavControlScript(self, map):
    """Returns the javascript for google maps navigation."""    
    if map.show_navcontrols:
      return "    %s.gmap.addControl(new GSmallMapControl());\n" % (map.id)
    else:
      return ""    
  
  def _GetMapControlScript(self, map):
    """Returns the javascript for google maps control."""    
    if map.show_mapcontrols:
      return "    %s.gmap.addControl(new GMapTypeControl());\n" % (map.id)
    else:
      return ""     
  
  def _GetMapScript(self, map):
    script_list = ["    %s_points = [" % map.id]
   
    # Constructs the points for the map 
    for point in map.points:
      script_list.append("[%f, %f, '%s', %s]" % point)
      script_list.append(",")
    script_list = script_list[:-1]
    script_list.append('];\n')
    js = ''.join(script_list)
    js = js.replace("u'", "'")
    
    js += ("    var %s = new map('%s', %s_points, %s, %s, %s);"
          "\n\n%s\n%s") % (map.id, map.id, map.id, map.center[0],
          map.center[1], map.zoom, self._GetMapControlScript(map), 
          self._GetNavControlScript(map))
    return js
  
  def _GetIconScript(self, icon):
    js = ("    var %s = new GIcon();\n"
          "    %s.image = \"%s\";\n"
          "    %s.shadow = \"%s\";\n"
          "    %s.shadowSize = new GSize(%s, %s);\n"
          "    %s.iconSize = new GSize(%s, %s);\n"
          "    %s.iconAnchor = new GPoint(%s, %s);\n"
          "    %s.infoWindowAnchor = new GPoint(%s, %s);\n" 
          "\n") % (icon.id, icon.id, icon.image, icon.id, icon.shadow, 
                     icon.id, icon.icon_size[0], icon.icon_size[1], icon.id, 
                     icon.shadow_size[0], icon.shadow_size[1], icon.id, 
                     icon.icon_anchor[0], icon.icon_anchor[1], icon.id, 
                     icon.info_window_anchor[0], icon.info_window_anchor[1])
    return js
    
  def _GetScriptForIcons(self):
    js = ""
    if (len(self.icons) > 0):
      for i in self.icons:
        js = js + self._GetIconScript(i)    
    return js
  
  def _GetScriptForMaps(self):
    js = ""
    for i in self.maps:
      js = js + self._GetMapScript(i)
    return js


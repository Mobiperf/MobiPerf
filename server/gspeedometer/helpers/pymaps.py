"""
*    Pymaps 0.9 
*    Copyright (C) 2007  Ashley Camba <stuff4ash@gmail.com> http://xthought.org
*
*    This program is free software; you can redistribute it and/or modify
*    it under the terms of the GNU General Public License as published by
*    the Free Software Foundation; either version 2 of the License, or
*    (at your option) any later version.
*
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU General Public License for more details.
*
*    You should have received a copy of the GNU General Public License
*    along with this program; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
"""   

class Icon:
  def __init__(self, id='icon'):
    self.id = id
    if self.id == "green_icon":
      self.image = """/static/green_location_pin.png"""
    elif self.id == "red_icon":
      self.image = """/static/red_location_pin.png"""
    self.shadow = ""
    self.iconSize = (15, 16)
    self.shadowSize = (18, 20)
    self.iconAnchor = (7, 16)
    self.infoWindowAnchor = (5, 1)

        
class Map:
  def __init__(self, id="map", pointlist=[]):
    self.id  = id    # div id        
    self.width = "500px"  # map div width
    self.height = "300px"  # map div height
    self.center = (0,0)     # center map latitute coordinate
    self.zoom  = "1"   # zoom level
    self.navcontrols  =  True   # show google map navigation controls
    self.mapcontrols  =  True   # show toogle map type (sat/map/hybrid) controls
    self.points = pointlist   # point list
    
  def __str__(self):
    return self.id
        
    
  def AddPoint(self, point):
    """ Add a point (lat,long) """
    self.points.append(point)

class GoogleMapWrapper:
  """ Python wrapper class for Google Maps API. """
  def __str__(self):
    return "Pymap"
  
  def __init__(self, key=None, maplist=[Map()], iconlist=[]):
    """ Default values """
    self.key      = key      # set your google key
    self.maps     = maplist
    self.icons    = iconlist
  
  def AddIcon(self,icon):
    self.icons.append(icon)
      
  def _navcontroljs(self, map):
    """ Returns the javascript for google maps control"""    
    if map.navcontrols:
      return "%s.gmap.addControl(new GSmallMapControl());\n" % (map.id)
    else:
      return ""    
  
  
  def _mapcontroljs(self, map):
    """ Returns the javascript for google maps control"""    
    if map.mapcontrols:
      return "%s.gmap.addControl(new GMapTypeControl());\n" % (map.id)
    else:
      return ""     
  
  
  def _showdivhtml(self, map):
    """ Returns html for dislaying map """
    html = """\n<div id=\"%s\">\n</div>\n""" % (map.id)
    return html
  
  def _mapjs(self, map):
    js = "%s_points = [" % map.id

    for point in map.points:
      js += "[%f, %f, '%s', %s]," % point
    js = js[:-1]
    js += '];\n'
    js = js.replace("u'", "'")
    
    js += """var %s = new Map('%s',%s_points,%s,%s,%s);
    \n\n%s\n%s""" % (map.id,map.id,map.id,map.center[0],
                     map.center[1],map.zoom, self._mapcontroljs(map), 
                     self._navcontroljs(map))
    return js
  
  def _iconjs(self,icon):
    js = """ 
    var %s = new GIcon(); 
    %s.image = "%s";  
    %s.shadow = "%s"; 
    %s.iconSize = new GSize(%s, %s);   
    %s.shadowSize = new GSize(%s, %s); 
    %s.iconAnchor = new GPoint(%s, %s); 
    %s.infoWindowAnchor = new GPoint(%s, %s); 
    \n\n """ % (icon.id, icon.id, icon.image, icon.id, icon.shadow, icon.id, 
                icon.iconSize[0],icon.iconSize[1],icon.id, 
                icon.shadowSize[0], icon.shadowSize[1], icon.id, 
                icon.iconAnchor[0],icon.iconAnchor[1], icon.id, 
                icon.infoWindowAnchor[0], icon.infoWindowAnchor[1])
    return js
    
  def _buildicons(self):
    js = ""
    if (len(self.icons) > 0):
      for i in self.icons:
        js = js + self._iconjs(i)    
    return js
  
  def _buildmaps(self):
    js = ""
    for i in self.maps:
      js = js + self._mapjs(i)
    return js

  def GetGoogleMapScript(self):
    """ Returns complete javacript for rendering map """
    
    self.js = """\n<script src=\"http://maps.google.com/maps?file=api&amp;v=2&amp;key=%s\" type="text/javascript"></script>
              <script type="text/javascript">
              //<![CDATA[
              function load() {
                if (GBrowserIsCompatible()) {
                
                function Point(lat,long,html,icon) {
                  this.gpoint = new GMarker(new GLatLng(lat,long),icon);
                  this.html = html;
                }               
                    
                function Map(id, points, lat, long, zoom) {
                  this.id = id;
                  this.points = points;
                  this.gmap = new GMap2(document.getElementById(this.id));
                  this.gmap.setCenter(new GLatLng(lat, long), zoom);
                  this.markerlist = markerlist;
                  this.addmarker = addmarker;
                  this.array2points = array2points;
                    
                  function markerlist(array) {
                      for (var i in array) {
                        this.addmarker(array[i]);
                      }
                  }
                  
                  function array2points(map_points) {            
                    for (var i in map_points) {  
                      points[i] = new Point(map_points[i][0],map_points[i][1],map_points[i][2],map_points[i][3]);         
                    }
                    return points;   
                  }                  
                  
                  function addmarker(point) {
                    if (point.html) {
                      GEvent.addListener(point.gpoint, "click", 
                        function() {
                          point.gpoint.openInfoWindowHtml(point.html);
                        });
                    }
                    this.gmap.addOverlay(point.gpoint);  
                  }
                  
                  this.points = array2points(this.points);
                  this.markerlist(this.points);
                  
                }  
                
                %s
                %s
                }
              }
              //]]>
              </script>
              
              
              """ % (self.key, self._buildicons(),self._buildmaps())
    return self.js 
      
  def showhtml(self):
    """returns a complete html page with a map"""
    
    self.html = """
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                <meta http-equiv="content-type" content="text/html; charset=utf-8"/>
                <title></title>
                %s
                </head>

                <body onload="load()" onunload="GUnload()">
                <div id="map" style="width: 500px; height: 300px"></div>
                </body>
                </html>
                """ % (self.pymapjs())
    return self.html


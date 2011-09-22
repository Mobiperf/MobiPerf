#!/usr/bin/python2.4
#
# Copyright 2011 Google Inc. All Rights Reserved.

"""Configuration options for the Speedometer service."""

__author__ = 'mdw@google.com (Matt Welsh)'

# Set of users with admin privileges
ADMIN_USERS = ['mdw@google.com', 'mattp@google.com']

NUM_PROPERTIES_IN_LIST = 25
NUM_MEASUREMENTS_IN_LIST = 25

GOOGLE_MAP_ZOOM = 15
DEFAULT_GOOGLEMAP_ICON_IMAGE = '/static/green_location_pin.png'
GOOGLEMAP_KEY = ('ABQIAAAAXVsx51W4RvTDuDUeIpF0qxRM6wioRijWnXUBkeVfSDD8OvINmRSa'
                 'z2Wa7XNxJDFBqSTkzyC0aVYxYw')

# Default viewport of map is Google's PKV office building in Seattle
DEFAULT_MAP_CENTER = (47.6508, -122.3515)

DEFAULT_MEASUREMENT_TYPE_FOR_VIEWING = 'ping'

# The number of markers to show on google map per device
GOOGLEMAP_MARKER_LIMIT = 500
# The number of timeseries points to retrieve
TIMESERIES_POINT_LIMIT = 250
# The number of devices to retrieve per user
NUM_DEVICES_PER_USER = 20
# The minimum ping delay in ms that we consider 'slow'
SLOW_PING_THRESHOLD_MS = 150
# The minimum dns lookup delay in ms that we consider 'slow'
SLOW_DNS_THRESHOLD_MS = 150
# The minimum number of hops reported by traceroute that we
# consider a long route
LONG_TRACEROUTE_HOP_COUNT_THRESHOLD = 14
# The interval in hours between any two adjacent battery records
# to show on the graph
BATTERY_INFO_INTERVAL_HOUR = 2
# The maximum time span in days the query covers
MAX_QUERY_INTERVAL_DAY = 31

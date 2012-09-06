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

"""Configuration options for the Mobiperf service."""

__author__ = 'mdw@google.com (Matt Welsh), drchoffnes@gmail.com (David Choffnes)'

NUM_PROPERTIES_IN_LIST = 5
NUM_MEASUREMENTS_IN_LIST = 20
NUM_DEVICES_IN_LIST = 20

GOOGLE_MAP_ZOOM = 15
DEFAULT_GOOGLEMAP_ICON_IMAGE = '/static/green_location_pin.png'
GOOGLEMAP_KEY = ('AIzaSyBGcqV5HgIdC-EXeO_pQOEBLrhz4bpRwZM')

# Default viewport of map is Google's PKV office building in Seattle
DEFAULT_MAP_CENTER = (47.6508, -122.3515)

DEFAULT_MEASUREMENT_TYPE_FOR_VIEWING = 'ping'

# default timezone set to PST (values are stored as UTC)
DEFAULT_TIMEZONE = 'pst'

# Amount to randomize lat/long of locations on the map
LOCATION_FUZZ_FACTOR = 0.001

# The number of markers to show on google map per device
GOOGLEMAP_MARKER_LIMIT = 500
# The number of timeseries points to retrieve
TIMESERIES_POINT_LIMIT = 100
# The total number of elements to fetch for a given query
QUERY_FETCH_LIMIT = 500
# The total number of elements to fetch for a large query
QUERY_FETCH_LIMIT_LARGE = 100000
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

# Timespan over which we consider a device to be active
ACTIVE_DAYS = 5

# Archive Settings
ARCHIVE_CONTENT_TYPE = 'application/zip'
ARCHIVE_CONTENT_DISPOSITION_BASE = 'attachment; filename="%s.zip"'
ARCHIVE_GS_BUCKET_PUBLIC = 'openmobiledata_public'
ARCHIVE_GS_ACL_PUBLIC = 'public-read'

# Archive anonymization settings
ANONYMIZE_FIELDS = ["user", "ip_address", "id"] # fields to remove from data
ANONYMIZE_LOCATION_PRECISION = 100 # number of sig figs is log(this)

#!/bin/bash
#
# Author: mdw@google.com (Matt Welsh)

# This script updates the Speedometer service running on AppEngine.

VERSION=`./set_version.sh`

appcfg.py -e $USER@google.com -A google.com:speedometer-dev update .

echo "Try going to http://$VERSION.speedometer-dev.googleplex.com"

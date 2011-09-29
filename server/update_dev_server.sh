#!/bin/bash
#
# Copyright 2011 Google Inc. All Rights Reserved.
# Author: mdw@google.com (Matt Welsh)

# This script updates the Speedometer service instance running on
# speedometer.googleplex.com.

VERSION=`./set_version.sh`

appcfg.py -e $USER@google.com -A google.com:speedometer-dev update .

echo "Try going to http://$VERSION.speedometer-dev.googleplex.com"

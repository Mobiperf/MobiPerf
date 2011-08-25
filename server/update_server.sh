#!/bin/bash
#
# Copyright 2011 Google Inc. All Rights Reserved.
# Author: mdw@google.com (Matt Welsh)

# This script updates the Speedometer service instance running on
# speedometer.googleplex.com.

echo "Password is: yeuqakmysyglasja"

VERSION=`./set_version.sh`

appcfg.py -e $USER@google.com update .

echo "Try going to http://$VERSION.speedometer.googleplex.com"

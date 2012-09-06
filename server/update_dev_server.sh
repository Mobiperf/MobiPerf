#!/bin/bash
#
# Author: mdw@google.com (Matt Welsh)

# This script updates the Speedometer service running on AppEngine.
# For more information on options try "appcfg.py --help" or
# https://developers.google.com/appengine/docs/python/tools/uploadinganapp

. ./script_config.sh

$PYTHON $APPCFG -e $USER_EMAIL -A $APP_ID update .

echo "Try going to http://$VERSION.$APP_ID.$APP_DOMAIN"

#!/bin/bash
#
# Author: gavaletz@google.com (Eric Gavaletz)

# Because of the growing number of scripts there was a growing number of
# dependencies and variables being set in lots of different locations.  This
# script is an attempt to get all of those in one place.  To use these
# in any script simply source it as such.  
#
# . ./script_config.sh
#
# Note that this will execute the contents on this script each time that it is
# sourced so nothing in this file should produce side effects.


# NOTE on python versions:
# Because a lot of systems have a default python version newer than 2.5 it is
# necessary to be more precise about which python we want to use for running
# the dev_appserver.  If you do not use 2.5, then it will give you warnings but
# more importantly it is possible that you could include something that works
# with the newer version of python that you use locally and it will break your
# code once it is uploaded to the development GAE instance.

PYTHON=python2.5
APPCFG=`which appcfg.py`
APPSERVER=`which dev_appserver.py`


APP_ID=`cat app.yaml.tmpl | grep application | cut -d " " -f 2`
APP_DOMAIN=appspot.com
VERSION=`./set_version.sh`


USER_EMAIL=`git config user.email`


# These are used for downloading data from the production environment, and using
# it with the dev_appserver.  See download_dev_data.sh and upload_local_data.sh
# for more information.

DATA_PATH=dev_data
DOWNLOAD_DATA_PATH=$DATA_PATH/datastore.sql3
DATASTORE_PATH=$DATA_PATH/dev_appserver.datastore


# These are used for running the local dev_appserver.  These will not effect the
# application as deployed on GAE.

DEBUG=""
#TODO(user) If having trouble try using this.
#DEBUG="-d"

CLEAN=""
#TODO(user) If having trouble with the datastore try uncommenting this.
#CLEAN="-c"

ADDRESS=""
#TODO(user) If you need to bind to a local address uncomment and adjust this.
#ADDRESS="--address=192.168.1.128"

#!/bin/sh
#
# Author: mdw@google.com (Matt Welsh)

# For more information on options try "dev_appserver.py --help" or
# https://developers.google.com/appengine/docs/python/tools/devserver

PYTHON=python
APPSERVER=`which dev_appserver.py`

BLOBSTORE_PATH=dev_data
DATASTORE_PATH=dev_data/dev_appserver.datastore

CLEAN=""
#TODO(user) if having trouble with the datastore try uncommenting this
#CLEAN="-c"

./set_version.sh --notag

$PYTHON $APPSERVER $CLEAN -d .
#TODO(user) use the following line for testing large data operations
#$PYTHON $APPSERVER $CLEAN -d --backends --blobstore_path=$BLOBSTORE_PATH \
#  --datastore_path=$DATASTORE_PATH .

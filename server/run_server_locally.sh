#!/bin/sh
#
# Author: mdw@google.com (Matt Welsh), gavaletz@google.com (Eric Gavaletz)

# For more information on options try "dev_appserver.py --help" or
# https://developers.google.com/appengine/docs/python/tools/devserver

. ./script_config.sh

$PYTHON $APPSERVER $CLEAN $DEBUG $ADDRESS .
#TODO(user) use the following line(s) for testing large data operations
#$PYTHON $APPSERVER $CLEAN $DEBUG $ADDRESS\
#  --high_replication \
#  --backends \
#  --use_sqlite \
#  --blobstore_path=$DATA_PATH \
#  --datastore_path=$DATASTORE_PATH .

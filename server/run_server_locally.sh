#!/bin/sh

PYTHON=python
APPSERVER=`which dev_appserver.py`

#CLEAN="-c"
CLEAN=""

./set_version.sh --notag

$PYTHON $APPSERVER $CLEAN -d --auth_domain google.com --address 0.0.0.0 .


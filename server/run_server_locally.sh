#!/bin/sh

PYTHON=python
APPSERVER=`which dev_appserver.py`

CLEAR=""
#CLEAR="-c"

$PYTHON $APPSERVER $CLEAR -d --auth_domain google.com --address 0.0.0.0 .



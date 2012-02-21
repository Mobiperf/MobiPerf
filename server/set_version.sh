#!/bin/bash
#
# Author: mdw@google.com (Matt Welsh)

# Tag a version of the codebase and expand the base.html template

DATESTRING=`date -u +'%Y%m%d-%H%M%S'`
GITVERSION=`git log --oneline -1 | awk '{print $1}'`
VERSIONID="$DATESTRING"-"$USER"-"$GITVERSION"

if [ "$1" != "--notag" ]; then
  git tag -a -m \
    "set_version.sh tagged from $GITVERSION at $DATESTRING by $USER" \
    $VERSIONID
fi

# Expand files
m4 \
  --define="__BUILDVERSION__=$VERSIONID" \
  --define="__BUILDUSER__=$USER" \
  --define="__BUILDDATE__=`date`" \
    gspeedometer/templates/base.html.tmpl > gspeedometer/templates/base.html

m4 \
  --define="__BUILDVERSION__=$VERSIONID" \
  --define="__BUILDUSER__=$USER" \
  --define="__BUILDDATE__=`date`" \
    app.yaml.tmpl > app.yaml

echo $VERSIONID

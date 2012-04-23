#!/bin/bash
#
# Author: gavaletz@google.com (Eric Gavaletz)

# This script downloads data from the Speedometer service running on AppEngine.
# For more information on options try "appcfg.py --help" or
# https://developers.google.com/appengine/docs/python/tools/uploadingdata#Downloading_and_Uploading_All_Data

# WARNING: this will produce a very heavy load on the GAE instance and eat up a
# lot of the applicaion's quota.  Please check to see if there is an existing
# download that you can use before using this.

. ./script_config.sh

mv $DATA_PATH $DATA_PATH.back

$PYTHON $APPCFG -e $USER_EMAIL -A s~$APP_ID download_data \
  --num_threads=10 \
  --batch_size=10 \
  --bandwidth_limit=1073741824 \
  --rps_limit=20 \
  --http_limit=7.5 \
  --url=http://$APP_ID.$APP_DOMAIN/_ah/remote_api \
  --log_file=$DATA_PATH/bulkloader-log-down \
  --db_filename=$DATA_PATH/bulkloader-progress-down.sql3 \
  --result_db_filename=$DATA_PATH/bulkloader-results-down.sql3 \
  --filename=$DOWNLOAD_DATA_PATH

#TODO(user) uncomment here if you want things kept clean
# This carries with it a risk of local data loss
#rm $DATASTORE_PATH $DATA_PATH.back

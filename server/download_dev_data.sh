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

DL_LOG_FILE=bulkloader-log-down
DL_PROGRESS_FILE=bulkloader-progress-down.sql3
DL_RESULTS_FILE=bulkloader-results-down.sql3

echo "downloading data from $APP_ID.$APP_DOMAIN into $DOWNLOAD_DATA_PATH"
echo "	--log_file=$DATA_PATH/$DL_LOG_FILE"
echo "	--db_filename=$DATA_PATH/$DL_PROGRESS_FILE"
echo "	--result_db_filename=$DATA_PATH/$DL_RESULTS_FILE"

$PYTHON $APPCFG -e $USER_EMAIL -A s~$APP_ID download_data \
  --num_threads=10 \
  --batch_size=10 \
  --bandwidth_limit=1073741824 \
  --rps_limit=20 \
  --http_limit=7.5 \
  --url=http://$APP_ID.$APP_DOMAIN/_ah/remote_api \
  --log_file=$DATA_PATH/$DL_LOG_FILE \
  --db_filename=$DATA_PATH/$DL_PROGRESS_FILE \
  --result_db_filename=$DATA_PATH/$DL_RESULTS_FILE \
  --filename=$DOWNLOAD_DATA_PATH

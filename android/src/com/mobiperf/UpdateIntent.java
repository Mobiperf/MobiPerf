/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mobiperf;


import android.content.Intent;

import java.security.InvalidParameterException;

/**
 * A repackaged Intent class that includes Mobiperf-specific information. 
 */
public class UpdateIntent extends Intent {
  
  // Different types of payloads that this intent can carry:
  public static final String STRING_PAYLOAD = "STRING_PAYLOAD";
  public static final String ERROR_STRING_PAYLOAD = "ERROR_STRING_PAYLOAD";
  public static final String PROGRESS_PAYLOAD = "PROGRESS_PAYLOAD";
  public static final String STATUS_MSG_PAYLOAD = "STATUS_MSG_PAYLOAD";
  public static final String STATS_MSG_PAYLOAD = "STATS_MSG_PAYLOAD";
  public static final String TASK_PRIORITY_PAYLOAD = "TASK_PRIORITY_PAYLOAD";
  
  // Different types of actions that this intent can represent:
  private static final String PACKAGE_PREFIX = UpdateIntent.class.getPackage().getName();
  public static final String MSG_ACTION = PACKAGE_PREFIX + ".MSG_ACTION";
  public static final String PREFERENCE_ACTION = PACKAGE_PREFIX + ".PREFERENCE_ACTION";
  public static final String MEASUREMENT_ACTION = PACKAGE_PREFIX + ".MEASUREMENT_ACTION";
  public static final String CHECKIN_ACTION = PACKAGE_PREFIX + ".CHECKIN_ACTION";
  public static final String CHECKIN_RETRY_ACTION = PACKAGE_PREFIX + ".CHECKIN_RETRY_ACTION";
  public static final String MEASUREMENT_PROGRESS_UPDATE_ACTION = 
      PACKAGE_PREFIX + ".MEASUREMENT_PROGRESS_UPDATE_ACTION";
  public static final String SYSTEM_STATUS_UPDATE_ACTION = 
      PACKAGE_PREFIX + ".SYSTEM_STATUS_UPDATE_ACTION";
  public static final String SCHEDULER_CONNECTED_ACTION = 
      PACKAGE_PREFIX + ".SCHEDULER_CONNECTED_ACTION";
  public static final String SCHEDULE_UPDATE_ACTION =
      PACKAGE_PREFIX + ".SCHEDULE_UPDATE_ACTION";
  public static final String RESULT_PAYLOAD = PACKAGE_PREFIX + ".RESULT_ACTION";
  
  /**
   * Creates an intent of the specified action with an optional message
   */
  protected UpdateIntent(String strMsg, String action) throws InvalidParameterException {
    super();
    if (action == null) {
      throw new InvalidParameterException("action of UpdateIntent should not be null");
    }
    this.setAction(action);
    this.putExtra(STRING_PAYLOAD, strMsg);
  }
}

// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;


import android.content.Intent;

import java.security.InvalidParameterException;

/**
 * A repackage Intent class that include Speedometer specific information 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class UpdateIntent extends Intent {
   
  // Different types of payload this intent can carry
  public static final String STRING_PAYLOAD = "STRING_PAYLOAD";
  // Different types of actions that this intent can represent
  private static final String PACKAGE_PREFIX = UpdateIntent.class.getPackage().getName();
  public static final String MSG_ACTION = PACKAGE_PREFIX + ".MSG_ACTION";
  public static final String PREFERENCE_ACTION = PACKAGE_PREFIX + ".PREFERENCE_ACTION";
  public static final String MEASUREMENT_ACTION = PACKAGE_PREFIX + ".MEASUREMENT_ACTION";
  public static final String CHECKIN_ACTION = PACKAGE_PREFIX + ".CHECKIN_ACTION";
  public static final String CHECKIN_RETRY_ACTION = PACKAGE_PREFIX + ".CHECKIN_RETRY_ACTION";
  
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

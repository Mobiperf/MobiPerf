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
  public static final String ACTION = PACKAGE_PREFIX + ".UPDATE_ACTION";
  
  /**
   * @param strMsg the message for the UI thread to print to the console
   */
  protected UpdateIntent(String strMsg) 
      throws InvalidParameterException {
    super();
    this.setAction(ACTION);
    this.putExtra(STRING_PAYLOAD, strMsg);
  }  
}

// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

/**
 * Error raised when a measurement fails.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
public class MeasurementError extends Exception {
  public MeasurementError(String reason) {
    super(reason);
  }
}

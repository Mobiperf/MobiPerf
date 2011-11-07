// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

/**
 * Subclass of MeasurementError that indicates that a measurement was skipped - a non-error result.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
public class MeasurementSkippedException extends MeasurementError {
  public MeasurementSkippedException(String reason) {
    super(reason);
  }

  public MeasurementSkippedException(String reason, Throwable e) {
    super(reason, e);
  }

}

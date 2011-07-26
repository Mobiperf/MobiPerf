// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.MeasurementScheduler;
import com.google.wireless.speed.speedometer.SpeedometerApp;

import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.TextView;

/**
 * Base class for test cases that involves UI. 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class TestMeasurementTaskBase extends 
    ActivityInstrumentationTestCase2<SpeedometerApp> {
  // The activity class through which we interact with the UI thread
  protected SpeedometerApp activity;
  // Required by the ActivityInstrumentationTestCase2 as shown in the Android tutorial
  protected Instrumentation inst;
  // The system console for the test case to print debugging message to the phone screen
  protected TextView systemConsole;
  protected MeasurementScheduler scheduler;  
  
  @SuppressWarnings("unchecked")
  public TestMeasurementTaskBase() {
    super("com.google.wireless.speed.speedometer.SpeedometerApp", SpeedometerApp.class);
  }
  
  @SuppressWarnings("unchecked")
  public TestMeasurementTaskBase(boolean isCheckinEnabled) {
    super("com.google.wireless.speed.speedometer.SpeedometerApp", SpeedometerApp.class);
    //TODO(Wenjie): Need a way to correctly initialize the scheduler on whether to enable checkin
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.activity = getActivity();
    this.inst = getInstrumentation();
    this.scheduler = this.activity.getScheduler();
    this.systemConsole = (TextView) 
        activity.findViewById(com.google.wireless.speed.speedometer.R.viewId.systemConsole);
  }
}

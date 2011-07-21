// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.SpeedometerApp;

import android.util.Log;

/**
 * Test of the scheduler based on UI output. Output on the server web page based to measurement
 * posting should also be verified. This test take 2 minutes!
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class TestSchedulerUi extends TestMeasurementTaskBase {
  public void testSchedulerBasedOnUi() {
    scheduler.resume();
    // First test a reasonable checkin interval
    scheduler.setCheckinInterval(20);
    scheduler.setIsCheckinEnabled(true);
    this.activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        systemConsole.requestFocus();
      }
    });
    
    // Monitor the the output of the scheduler from the system console on the phone
    try {
      Thread.sleep(1000 * 60);
    } catch (InterruptedException e) {
      Log.i(SpeedometerApp.TAG, "Test case sleep interrupted");
    }
    
    // Stress test: checkin every two seconds, tons of tasks
    scheduler.setCheckinInterval(2);
    // Monitor the the output of the scheduler from the system console on the phone
    try {
      Thread.sleep(1000 * 60);
    } catch (InterruptedException e) {
      Log.i(SpeedometerApp.TAG, "Test case sleep interrupted");
    }
    
    this.inst.waitForIdleSync();
  }
}

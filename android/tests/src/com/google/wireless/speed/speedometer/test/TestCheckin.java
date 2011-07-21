// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.Checkin;
import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.SpeedometerApp;

import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * Test the basic checkin without the scheduler
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class TestCheckin extends TestMeasurementTaskBase {
  
  /**
   * Test the checkin by manually invoking the methods
   */
  public void testCheckin() {
    Checkin checkin = new Checkin(this.getActivity());
    checkin.getCookie();
    try {
      List<MeasurementTask> list = checkin.checkin();
      assertNotNull(list);
      assertTrue("List is empty", list.size() > 0);
      Log.i(SpeedometerApp.TAG, "Got " + list.size() + " new tasks");
      for (MeasurementTask task : list) {
        Log.i(SpeedometerApp.TAG, task.toString());
      }
    } catch (IOException e) {
      assertTrue(e.getMessage(), false);
    }
  }
}

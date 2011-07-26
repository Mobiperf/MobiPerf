// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.SpeedometerApp;
import com.google.wireless.speed.speedometer.measurements.HttpTask;
import com.google.wireless.speed.speedometer.measurements.HttpTask.HttpDesc;

import android.util.Log;

import java.util.HashMap;

/**
 * Test case for the HTTP measurement
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class TestHttpTask extends TestMeasurementTaskBase {
  public TestHttpTask() {
    // Disable the auto checkin so that our manually inserted task can be run
    super(false);
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }
  
  /* TODO(Wenjie): Make this test case to be more self-contained without depending
   * on the external links */
  public void testHttpTask() {
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("url", "www.google.com");
    //params.put("url", "inst.eecs.berkeley.edu/~cs150/Documents/CC2420.pdf");
    params.put("method", "GET");
    HttpDesc desc = new HttpDesc(null, null, null, 0, 0, 0, params);
    HttpTask task = new HttpTask(desc, this.activity);
    assertTrue(task != null);
    
    this.activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        systemConsole.requestFocus();
      }      
    });
    
    // submitTask will notify the waiting scheduler thread upon success
    this.scheduler.submitTask(task);
    
    /* TODO(Wenjie): Before we know how to figure out the output of a HTTP
     * measurement is correct, we simply use this test case as a driver to submit
     * task and verify monitor output from the phone
     */
    try {
      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      Log.i(SpeedometerApp.TAG, "Test case sleep interrupted");
    }
    
    this.inst.waitForIdleSync();
  }
}

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
package com.mobiperf.speedometer.test;

import com.mobiperf.measurements.TracerouteTask;
import com.mobiperf.measurements.TracerouteTask.TracerouteDesc;
import com.mobiperf.speedometer.R;
import com.mobiperf.SpeedometerApp;
import com.mobiperf.util.MeasurementJsonConvertor;

import android.util.Log;

import java.util.HashMap;

/**
 * Test case for the Traceroute measurement 
 */
public class TestTracerouteTask extends TestMeasurementTaskBase {
  
  public TestTracerouteTask() {
    // Disable the auto checkin so that our manually inserted task can be run
    super(false);
  }
  
  /* TODO(Wenjie): Make this test case to be more self-contained without depending
   * on the external links */
  public void testPingTask() {
    String pingExe = this.activity.getString(R.string.ping_executable);
    String target = "www.dealsea.com";

    HashMap<String, String> params = new HashMap<String, String>();
    params.put("ping_exe", pingExe);
    params.put("target", target);
    params.put("max_hop_count", String.valueOf(100));
    TracerouteDesc desc = new TracerouteDesc(null, null, null, 0, 1, 0, params);
    Log.i(SpeedometerApp.TAG, MeasurementJsonConvertor.toJsonString(desc));
    TracerouteTask task = new TracerouteTask(desc, this.activity);
    
    // submitTask will notify the waiting scheduler thread upon success
    this.scheduler.submitTask(task);
    this.scheduler.resume();
    
    /* TODO(Wenjie): Before we figure out how to verify the output of a traceroute
     * measurement is correct, we simply use this test case as a driver to submit
     * task and verify the output by manually inspecting the printout on the phone
     */
    try {
      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      Log.i(SpeedometerApp.TAG, "Test case sleep interrupted");
    }
    this.inst.waitForIdleSync();
  }
}

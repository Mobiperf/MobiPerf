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
package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.MeasurementDesc;
import com.google.wireless.speed.speedometer.R;
import com.google.wireless.speed.speedometer.SpeedometerApp;
import com.google.wireless.speed.speedometer.measurements.PingTask;
import com.google.wireless.speed.speedometer.measurements.PingTask.PingDesc;
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;

import android.util.Log;

import java.util.HashMap;

/**
 * Test case for the Ping measurement
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class TestPingTask extends TestMeasurementTaskBase {

  public TestPingTask() {
    // Disable the auto checkin so that our manually inserted task can be run
    super(false);
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }
  
  /* TODO(Wenjie): Make this test case to be more self-contained without depending
   * on the external links */
  public void testPingTask() {
    String pingExe = this.activity.getString(R.string.ping_executable);
    String pingServer = "www.randomhostname.com";

    HashMap<String, String> params = new HashMap<String, String>();
    params.put("ping_exe", pingExe);
    params.put("target", pingServer);    
    PingDesc pingDesc = new PingDesc(null, null, null, 0, 0, 0, params);
    Log.i(SpeedometerApp.TAG, MeasurementJsonConvertor.toJsonString(pingDesc));
    MeasurementDesc desc;
    PingTask pingTask = new PingTask(pingDesc, this.activity);
    
    this.activity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        systemConsole.requestFocus();
      }      
    });
    
    // submitTask will notify the waiting scheduler thread upon success
    this.scheduler.submitTask(pingTask);
    
    /* TODO(Wenjie): Before we figure out how to verify the output of a ping
     * measurement is correct, we simply use this test case as a driver to submit
     * task and verify the output by manually inspecting the printout on the phone
     */
    try {
      Thread.sleep(20000);
    } catch (InterruptedException e) {
      Log.i(SpeedometerApp.TAG, "Test case sleep interrupted");
    }
    this.inst.waitForIdleSync();
  }
}

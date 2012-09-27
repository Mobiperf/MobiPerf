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

import com.mobiperf.speedometer.Checkin;
import com.mobiperf.speedometer.MeasurementTask;
import com.mobiperf.speedometer.SpeedometerApp;

import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * Test the basic checkin without the scheduler
 */
public class TestCheckin extends TestMeasurementTaskBase {
  
  /**
   * Test the checkin by manually invoking the methods
   */
  public void testCheckin() {
    Checkin checkin = new Checkin(this.scheduler);
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

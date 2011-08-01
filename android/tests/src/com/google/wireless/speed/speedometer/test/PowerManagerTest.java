// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.SpeedometerApp;
import com.google.wireless.speed.speedometer.test.TestMeasurementTaskBase.DummyTask.DummyDesc;

import android.util.Log;

/**
 * The unit test case for the power manager. The unit test works only when the phone is 
 * unplugged, otherwise BatteryCapPowerManager.canScheduleExperiment will always return
 * true.
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class PowerManagerTest extends TestMeasurementTaskBase {
  
  private static final int DUMMY_TASK_BATCH_COUNT = 10;
  
  private void waitALittle() {
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Log.e(SpeedometerApp.TAG, "sleep interrupted");
    }
  }
  
  public void testBasics() {
    this.scheduler.resume();
    this.scheduler.setIsCheckinEnabled(false);
    this.scheduler.removeAllUnscheduledTasks();
    // First set the battery cap to 100, so no measurement will be scheduled
    this.scheduler.getPowerManager().setBatteryCap(100);
    DummyDesc desc = new DummyDesc(null, null, null, null, 0, 1, 0, null);
    DummyTask task = new DummyTask(desc, this.activity);
    this.scheduler.submitTask(task);
    waitALittle();
    assertTrue(this.scheduler.getPendingTaskCount() == 0);
    // Then sets it to 0, then we should be able to schedule
    this.scheduler.getPowerManager().setBatteryCap(0);
    waitALittle();
    assertTrue(this.scheduler.getPendingTaskCount() == 1);
    // Setting threshold back to 100, now we cannot schedule new task again
    this.scheduler.getPowerManager().setBatteryCap(100);
    for (int i = 0; i < DUMMY_TASK_BATCH_COUNT; i++) {
      task = new DummyTask(desc, this.activity);
      this.scheduler.submitTask(task);
    }
    waitALittle();
    assertTrue(this.scheduler.getPendingTaskCount() == 1);
    this.scheduler.getPowerManager().setBatteryCap(0);
    // Give some time for the schedule to schedule new tasks
    waitALittle();
    assertTrue(this.scheduler.getPendingTaskCount() == 1 + DUMMY_TASK_BATCH_COUNT);
  }
}

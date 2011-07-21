// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.SpeedometerApp;
import com.google.wireless.speed.speedometer.measurements.HttpTask;
import com.google.wireless.speed.speedometer.measurements.HttpTask.HttpDesc;

import android.util.Log;

import java.util.HashMap;

/**
 * Test baseic task manipulation and one-shot checkin on the scheduler 
 * @author wenjiezeng@google.com (Steve Zeng)
 */
public class TestSchedulerBasic extends TestMeasurementTaskBase { 
  /* TODO(Wenjie): create mock objects of tasks and the scheduler for even better testing.
   * For better testing, we need more internal information about the scheduler which is 
   * not appropriate for public access.
   */
  
  // this constant reflects what's been set on the server
  private static final int TASKS_PER_CHECKIN = 2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }
    
  public void testNullTaskAddition() {
    scheduler.setIsCheckinEnabled(false);
    scheduler.pause();
    
    // pre-condition
    assertTrue(scheduler.getUnscheduledTaskCount() == 0);
    assertTrue(scheduler.getNextTaskToBeScheduled() == null);
    // action
    assertFalse(scheduler.submitTask(null));
    // post-condition
    assertTrue(scheduler.getUnscheduledTaskCount() == 0);
    assertTrue(scheduler.getNextTaskToBeScheduled() == null);
    
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("url", "www.google.com");
    //params.put("url", "inst.eecs.berkeley.edu/~cs150/Documents/CC2420.pdf");
    params.put("method", "GET");
    HttpDesc desc = new HttpDesc(null, null, null, 0, 0, 0, params);
    HttpTask task = new HttpTask(desc, this.activity);
    
    assertTrue(task != null);
    // action
    assertTrue(scheduler.submitTask(task));
    assertTrue("actual taskQueueLength is " + scheduler.getUnscheduledTaskCount(), 
        scheduler.getUnscheduledTaskCount() == 1);
    assertTrue(scheduler.getNextTaskToBeScheduled() == task);
    
    // action
    assertFalse(scheduler.submitTask(null));
    assertTrue("actual taskQueueLength is " + scheduler.getUnscheduledTaskCount(), 
      scheduler.getUnscheduledTaskCount() == 1);
    assertTrue(scheduler.getNextTaskToBeScheduled() == task);
  }
  
  /** Test the ordering of tasks of the same priority*/
  public void testPriorityQueueOnStartTime() {
    scheduler.removeAllUnscheduledTasks();
    scheduler.setIsCheckinEnabled(false);
    scheduler.pause();
    assertTrue(scheduler.getUnscheduledTaskCount() == 0);
    assertTrue(scheduler.getNextTaskToBeScheduled() == null);
    
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("url", "www.google.com");
    //params.put("url", "inst.eecs.berkeley.edu/~cs150/Documents/CC2420.pdf");
    params.put("method", "GET");
    long currentTime = System.currentTimeMillis(); 
    
    // add a number of tasks with a start time after the currentTime
    for (int i = 0; i < 10; i++) {
      HttpDesc desc = new HttpDesc(null, null, null, 0, 0, 0, params);
      desc.startTime.setTime(currentTime + i + 10);
      HttpTask task = new HttpTask(desc, this.activity);
      assertTrue(scheduler.submitTask(task));
    }
    assertTrue(scheduler.getUnscheduledTaskCount() == 10);
    /* Although we add the first task at last, it should still be put into the head because
     * it has lowest startTime */
    HttpDesc desc = new HttpDesc(null, null, null, 0, 0, 0, params);
    desc.startTime.setTime(currentTime);
    HttpTask firstTask = new HttpTask(desc, this.activity);
    assertTrue(scheduler.submitTask(firstTask));
    assertTrue(scheduler.getUnscheduledTaskCount() == 11);
    assertTrue(scheduler.getNextTaskToBeScheduled() == firstTask);
  }
  
  /** Test the ordering of tasks of the same priority*/
  public void testPriorityQueueOnPriority() {
    scheduler.removeAllUnscheduledTasks();
    scheduler.setIsCheckinEnabled(false);
    scheduler.pause();
    assertTrue(scheduler.getUnscheduledTaskCount() == 0);
    assertTrue(scheduler.getNextTaskToBeScheduled() == null);
    
    HashMap<String, String> params = new HashMap<String, String>();
    params.put("url", "www.google.com");
    //params.put("url", "inst.eecs.berkeley.edu/~cs150/Documents/CC2420.pdf");
    params.put("method", "GET");
    long priority = 0;
    long currentTime = System.currentTimeMillis();
    
    // add a number of tasks with a start time after the currentTime
    for (int i = 0; i < 10; i++) {
      HttpDesc desc = new HttpDesc(null, null, null, 0, 0, priority - i, params);
      desc.startTime.setTime(currentTime + i * 100);
      HttpTask task = new HttpTask(desc, this.activity);
      assertTrue(scheduler.submitTask(task));
      // task with the lowest priority is put in the head, even if its start time is later 
      assertTrue(scheduler.getNextTaskToBeScheduled() == task);
    }
    assertTrue(scheduler.getUnscheduledTaskCount() == 10);
    /* Although we add the user task at last, it should still be put into the head because
     * it has highest priority (the lower the priority value, the higher the priority) */
    HttpDesc desc = new HttpDesc(null, null, null, 0, 0, MeasurementTask.USER_PRIORITY, params);
    desc.startTime.setTime(Long.MAX_VALUE);
    HttpTask userTask = new HttpTask(desc, this.activity);
    assertTrue(scheduler.submitTask(userTask));
    assertTrue(scheduler.getUnscheduledTaskCount() == 11);
    assertTrue(scheduler.getNextTaskToBeScheduled() == userTask);
  }
  
  /** Allow the scheduler to checkin once and verify checkin results */
  public void testSchedulerCheckin() {
    int checkinInterval = 30;
    scheduler.removeAllUnscheduledTasks();
    scheduler.resume();
    scheduler.setCheckinInterval(checkinInterval);
    scheduler.setIsCheckinEnabled(true);
    // give some time for the scheduler to finish the initiated checkin but before we 
    // get to the next checkin
    try {
      Thread.sleep(1000 * checkinInterval / 2);
    } catch (InterruptedException e) {
      Log.i(SpeedometerApp.TAG, "Test case sleep interrupted");
    }
    
    assertTrue(scheduler.getUnscheduledTaskCount() == 0);
    assertTrue("there are " + scheduler.getPendingTaskCount() + " in pendingTasks", 
        scheduler.getPendingTaskCount() == TASKS_PER_CHECKIN);
    scheduler.requestStop();
  }
}

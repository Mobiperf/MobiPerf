// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.util.PhoneUtils;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;
import java.util.concurrent.Callable;

/**
 * A basic power manager implementation that decides whether a measurement can be scheduled
 * based on the current battery level: no measurements will be scheduled if the current battery
 * is lower than a threshold.
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class BatteryCapPowerManager {
  /** The minimum threshold below which no measurements will be scheduled */
  private int minBatteryThreshold;
    
  public BatteryCapPowerManager(int batteryThresh, Context context) {
    this.minBatteryThreshold = batteryThresh;
  }
  
  /** 
   * Sets the minimum battery percentage below which measurements cannot be run.
   * 
   * @param batteryThresh the battery percentage threshold between 0 and 100
   */
  public synchronized void setBatteryThresh(int batteryThresh) throws IllegalArgumentException {
    if (batteryThresh < 0 || batteryThresh > 100) {
      throw new IllegalArgumentException("batteryCap must fall between 0 and 100, inclusive");
    }
    this.minBatteryThreshold = batteryThresh;
  }
  
  public synchronized int getBatteryThresh() {
    return this.minBatteryThreshold;
  }
  
  /** 
   * Returns whether a measurement can be run.
   */
  public synchronized boolean canScheduleExperiment() {
    return (PhoneUtils.getPhoneUtils().isCharging() || 
        PhoneUtils.getPhoneUtils().getCurrentBatteryLevel() > minBatteryThreshold);
  }
  
  /**
   * A task wrapper that is power aware, the real logic is carried out by realTask
   * 
   * @author wenjiezeng@google.com (Steve Zeng)
   *
   */
  public static class PowerAwareTask implements Callable<MeasurementResult> {
    
    private MeasurementTask realTask;
    private BatteryCapPowerManager pManager;
    private MeasurementScheduler scheduler;
    
    public PowerAwareTask(MeasurementTask task, BatteryCapPowerManager manager, 
                          MeasurementScheduler scheduler) {
      realTask = task;
      pManager = manager;
      this.scheduler = scheduler;
    }
    
    private void broadcastMeasurementStart() {
      Log.i(SpeedometerApp.TAG, "Starting PowerAwareTask " + realTask);
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.STATUS_MSG_PAYLOAD, "Running " + realTask.getDescriptor());
      
      scheduler.sendBroadcast(intent);
    }
    
    private void broadcastMeasurementEnd(MeasurementResult result, MeasurementError error) {
      Log.i(SpeedometerApp.TAG, "Ending PowerAwareTask " + realTask);
      /* Only broadcast information about measurements if we are above battery threshold and
       * that the scheduler is not paused. Otherwise, the measurement is simply skipped and we
       * should not print anything about it.
       */
      if (pManager.canScheduleExperiment() && !scheduler.isPauseRequested()) {
        Intent intent = new Intent();
        intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, 
            (int) realTask.getDescription().priority);
        // A progress value MEASUREMENT_END_PROGRESS indicates the end of an measurement
        intent.putExtra(UpdateIntent.PROGRESS_PAYLOAD, Config.MEASUREMENT_END_PROGRESS);
        if (result != null) {
          intent.putExtra(UpdateIntent.STRING_PAYLOAD, result.toString());
        } else {
          String errorString = "Measurement " + realTask.toString() + " failed. ";
          errorString += "\n\nTimestamp: " + Calendar.getInstance().getTime();
          if (error != null) {
            errorString += "\n\n" + error.toString();
          } 
          intent.putExtra(UpdateIntent.STRING_PAYLOAD, errorString);
        }
        
        scheduler.sendBroadcast(intent);
      }
      
      scheduler.refreshNotificationAndStatusBar();
    }
    
    @Override
    public MeasurementResult call() throws MeasurementError {
      MeasurementResult result = null;
      try {
        PhoneUtils.getPhoneUtils().acquireWakeLock();
        if (scheduler.isPauseRequested()) {
          throw new MeasurementError("Scheduler is paused.");
        }
        if (!pManager.canScheduleExperiment()) {
          scheduler.refreshNotificationAndStatusBar();
          throw new MeasurementError("Not enough power");
        }
        scheduler.setCurrentTask(realTask);
        broadcastMeasurementStart();
        try {
          Log.i(SpeedometerApp.TAG, "Calling PowerAwareTask " + realTask);
          result = realTask.call(); 
          Log.i(SpeedometerApp.TAG, "Got result " + result);
          broadcastMeasurementEnd(result, null);
          return result;
        } catch (MeasurementError e) {
          Log.e(SpeedometerApp.TAG, "Got MeasurementError running task", e);
          broadcastMeasurementEnd(null, e);
          throw e;
        } catch (Exception e) {
          Log.e(SpeedometerApp.TAG, "Got exception running task", e);
          MeasurementError err = new MeasurementError("Got exception running task", e);
          broadcastMeasurementEnd(null, err);
          throw err;
        }
      } finally {
        PhoneUtils.getPhoneUtils().releaseWakeLock();
        scheduler.setCurrentTask(null);
      }
    }
  }
}

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

package com.mobiperf;

import com.mobiperf.util.PhoneUtils;

import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.concurrent.Callable;

/**
 * A basic power manager implementation that decides whether a measurement can be scheduled
 * based on the current battery level: no measurements will be scheduled if the current battery
 * is lower than a threshold.
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
      Logger.i("Starting PowerAwareTask " + realTask);
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.STATUS_MSG_PAYLOAD, "Running " + realTask.getDescriptor());
      
      scheduler.sendBroadcast(intent);
    }
    
    private void broadcastMeasurementEnd(MeasurementResult result, MeasurementError error) {
      Logger.i("Ending PowerAwareTask " + realTask);
      // Only broadcast information about measurements if they are true errors.
      if (!(error instanceof MeasurementSkippedException)) {
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
          intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);
        }
        scheduler.sendBroadcast(intent);
      }
      scheduler.updateStatus();
    }
    
    @Override
    public MeasurementResult call() throws MeasurementError {
      MeasurementResult result = null;
      scheduler.sendStringMsg("Running:\n" + realTask.toString());
      try {
        PhoneUtils.getPhoneUtils().acquireWakeLock();
        if (scheduler.isPauseRequested()) {
          Logger.i("Skipping measurement - scheduler paused");
          throw new MeasurementSkippedException("Scheduler paused");
        }
        if (!pManager.canScheduleExperiment()) {
          Logger.i("Skipping measurement - low battery");
          throw new MeasurementSkippedException("Not enough battery power");
        }
        if (PhoneUtils.getPhoneUtils().getNetwork() == PhoneUtils.NETWORK_WIFI) {
          Logger.i("Skipping measurement - on wifi");
          throw new MeasurementSkippedException("Connected via WiFi");
        }
        scheduler.setCurrentTask(realTask);
        broadcastMeasurementStart();
        try {
          Logger.i("Calling PowerAwareTask " + realTask);
          result = realTask.call(); 
          Logger.i("Got result " + result);
          broadcastMeasurementEnd(result, null);
          return result;
        } catch (MeasurementError e) {
          Logger.e("Got MeasurementError running task", e);
          broadcastMeasurementEnd(null, e);
          throw e;
        } catch (Exception e) {
          Logger.e("Got exception running task", e);
          MeasurementError err = new MeasurementError("Got exception running task", e);
          broadcastMeasurementEnd(null, err);
          throw err;
        }
      } finally {
        PhoneUtils.getPhoneUtils().releaseWakeLock();
        scheduler.setCurrentTask(null);
        scheduler.sendStringMsg("Done running:\n" + realTask.toString());
      }
    }
  }
}

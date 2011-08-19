// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.content.Context;

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
    
    public PowerAwareTask(MeasurementTask task, BatteryCapPowerManager manager) {
      realTask = task;
      pManager = manager;
    }
    
    @Override
    public MeasurementResult call() throws MeasurementError {
      try {
        if (!pManager.canScheduleExperiment()) {
          throw new MeasurementError("Not enough power");
        }
        return realTask.call();
      } finally {
        PhoneUtils.getPhoneUtils().releaseWakeLock();
      }
    }
  }
}

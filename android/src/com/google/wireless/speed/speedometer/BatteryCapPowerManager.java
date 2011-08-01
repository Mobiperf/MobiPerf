// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.ArrayList;

/**
 * A basic power manager implementation that decides whether a measurement can be scheduled
 * based on the current battery level: no measurements will be scheduled if the current battery
 * is lower than a threshold.
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class BatteryCapPowerManager {
  /** The default battery level if we cannot read it from the system */
  private static final int DEFAULT_BATTERY_LEVEL = 0;
  /** The default maximum battery level if we cannot read it from the system */
  private static final int DEFAULT_BATTERY_SCALE = 100;
  /** The default state of 0 means that the phone is on battery by default */
  private static final int DEFAULT_PLUGGED_STATE = 0;
  
  /** The application context needed to receive intent broadcasts. */
  private Context context;
  /** The minimum threshold below which no measurements will be scheduled*/
  private int batteryCap;
  /** Tells whether the phone is plugged to some power source*/
  private boolean isPlugged;
  /** Current battery level in percentage */ 
  private int curBatLevel;
  /** Listeners registered by clients who will be notified upon battery changes */
  private ArrayList<PowerManagerListener> listeners = 
      new ArrayList<PowerManagerListener>();
  /** Receiver that handles batter change broadcast intents */
  private BroadcastReceiver broadcastReceiver;
  
  public BatteryCapPowerManager(int batteryCap, Context context) {
    this.batteryCap = batteryCap;
    this.context = context;
    this.broadcastReceiver = new PowerStateChangeReceiver();
    // Registers a receiver for battery change events.
    Intent powerIntent = context.registerReceiver(broadcastReceiver, 
        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    updateBatteryStat(powerIntent);
  }
  
  /** 
   * Sets the minimum battery percentage below which measurements cannot be run.
   * 
   * @param batteryCap the batter percentage threshold between 0 and 100
   */
  public synchronized void setBatteryCap(int batteryCap) throws IllegalArgumentException {
    if (batteryCap < 0 || batteryCap > 100) {
      throw new IllegalArgumentException("batteryCap must fall between 0 and 100, inclusive");
    }
    this.batteryCap = batteryCap;
    if (canScheduleExperiment()) {
      this.notifyListeners();
    }
  }
  
  public synchronized int getBatteryCap() {
    return this.batteryCap;
  }
  
  /** 
   * Returns whether a measurement can be run.
   */
  public synchronized boolean canScheduleExperiment() {
    return (isPlugged || curBatLevel > batteryCap);
  }

  /** 
   * Sets the listener for power manager events. The listeners receive onPowerStateChange() 
   * calls when measurements can be scheduled. 
   */
  public synchronized void setOnStateChangeListener(PowerManagerListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      Log.i(SpeedometerApp.TAG, "listener added to monitor battery change event");
      this.listeners.add(listener);
    }
  }
  
  /** 
   * Part of stopping Speedometer: to unregister the broadcast receiver. 
   */
  public void stop() {
    Log.i(SpeedometerApp.TAG, "Unregistering BroadcastReceiver from ACTION_BATTERY_CHANGED");
    context.unregisterReceiver(broadcastReceiver);
  }
  
  private void notifyListeners() {
    for (PowerManagerListener listener : listeners) {
      listener.onPowerStateChange();
    }
  }
  
  private synchronized void updateBatteryStat(Intent powerIntent) {
    int scale = powerIntent.getIntExtra(BatteryManager.EXTRA_SCALE, DEFAULT_BATTERY_SCALE);
    int level = powerIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, DEFAULT_BATTERY_LEVEL);
    // change to the unit of percentage
    this.curBatLevel = (int) ((double) level * 100 / scale);
    // a return integer of 0 means the phone is on battery
    this.isPlugged = powerIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 
        DEFAULT_PLUGGED_STATE) != 0;
    
    Log.i(SpeedometerApp.TAG, 
        "Current power level is " + curBatLevel + " and isPlugged = " + isPlugged);
    if (canScheduleExperiment()) {
      this.notifyListeners();
    }
  }
  
  private class PowerStateChangeReceiver extends BroadcastReceiver {
    /** 
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context, 
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
      updateBatteryStat(intent);
    }
  }
  
  /**
   * Listener for power manager events.
   * 
   * @author wenjiezeng@google.com (Steve Zeng)
   */
  public static interface PowerManagerListener {
    /**
     * Callback whenever the power manager has resource to schedule new tasks.
     */
    public void onPowerStateChange();
  }
}

// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.ArrayList;

/**
 * A basic power manager implementation that decides whether a measurement can be scheduled
 * based on the periodic, say daily, quota on how many experiments can run. The quota is spread
 * evenly onto each hour in the cycle. Listeners are notified for new resource per hour.
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class BatteryCapPowerManager {
  // The default battery level if we cannot read it from the system
  private static final int DEFAULT_BATTERY_LEVEL = 0;
  // The default maximum battery level if we cannot read it from the system
  private static final int DEFAULT_BATTERY_SCALE = 100;
  private static final int DEFAULT_PLUGGED_STATE = 0;
  
  private Context context;
  private int batteryCap;
  private boolean isPlugged;
  // Current battery level in percentage 
  private int curBatLevel;
  private ArrayList<PowerManagerListener> listeners = 
      new ArrayList<PowerManagerListener>();
  
  public BatteryCapPowerManager(int batteryCap, Context context) {
    this.batteryCap = batteryCap;
    this.context = context;
    Intent powerIntent = context.registerReceiver(new PowerStateChangeReceiver(), 
        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    updateBatteryStat(powerIntent);
  }
  
  /** Sets the minimum battery percentage below which measurements cannot be run */
  public synchronized void setBatteryCap(int batteryCap) {
    this.batteryCap = batteryCap;
    updateBatteryStat();
  }
  
  /** 
   * Returns whether a measurement can be run
   */
  public synchronized boolean canScheduleExperiment() {
    return (this.isPlugged || curBatLevel > this.batteryCap);
  }

  /** Sets the listener for power manager events. The listeners receive onPowerStateChange() 
   * calls when measurements can be scheduled */
  public synchronized void setOnStateChangeListener(PowerManagerListener listener) {
    if (listener != null && !listeners.contains(listener)) {
      this.listeners.add(listener);
    }
  }
  
  public void stop() {
    this.context.unregisterReceiver(receiver);
  }
  
  private void notifyListeners() {
    for (PowerManagerListener listener : listeners) {
      listener.onPowerStateChange();
    }
  }
  
  private void updateBatteryStat() {
    Intent powerIntent = context.registerReceiver(null, 
      new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    updateBatteryStat(powerIntent);
  }
  
  private synchronized void updateBatteryStat(Intent powerIntent) {
    int scale = powerIntent.getIntExtra(BatteryManager.EXTRA_SCALE, DEFAULT_BATTERY_SCALE);
    int level = powerIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, DEFAULT_BATTERY_LEVEL);
    // change to the unit of percentage
    this.curBatLevel = (int) ((double) level * 100 / scale);
    // a return integer of 0 means the phone is on battery
    this.isPlugged = powerIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 
        DEFAULT_PLUGGED_STATE) != 0;
    
    if (canScheduleExperiment()) {
      this.notifyListeners();
    }
  }
  
  private class PowerStateChangeReceiver extends BroadcastReceiver {

    /* 
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
      updateBatteryStat(intent);
    }
    
  }
  
  /**
   * Listener interface for power manager events
   * @author wenjiezeng@google.com (Steve Zeng)
   *
   */
  public static interface PowerManagerListener {
    /**
     * Callback whenever the power manager has resource to schedule new tasks
     */
    public void onPowerStateChange();
  }
}

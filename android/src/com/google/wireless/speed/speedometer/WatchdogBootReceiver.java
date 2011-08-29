// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * A broadcast receiver that starts SpeedomterApp upon the BOOT_COMPLETED event.
 *
 * @author wenjiezeng@google.com (Wenjie Zeng)
 *
 */
public class WatchdogBootReceiver extends BroadcastReceiver {

  @Override
  final public void onReceive(Context context, Intent intent) {
    Log.i(SpeedometerApp.TAG, "Boot intent received.");
    Intent serviceIntent = new Intent(context, MeasurementScheduler.class);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    
    if (prefs.getBoolean(context.getString(R.string.startOnBootPrefKey),
        Config.DEFAULT_START_ON_BOOT)) {
      Log.i(SpeedometerApp.TAG, "Starting MeasurementScheduler from watch dog");
      context.startService(serviceIntent);
    }
  }
}

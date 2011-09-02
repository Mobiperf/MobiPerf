// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.ListView;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class SystemConsoleActivity extends Activity {
  public static final String TAB_TAG = "MEASUREMENT_MONITOR";
  
  private ListView consoleView;
  private BroadcastReceiver receiver;
  private MeasurementScheduler scheduler = null;
  
  public SystemConsoleActivity() {
    // This receiver only receives intent actions generated from UpdateIntent
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        /* The content of the console is maintained by the scheduler. We simply hook up the 
         * view with the content here. */
        if (scheduler == null) {
          SpeedometerApp parent = (SpeedometerApp) getParent();
          scheduler = parent.getScheduler();
          if (scheduler != null) {
            consoleView.setAdapter(scheduler.systemConsole);
          }
        }
      }
    };
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.console);
    // Register activity specific BroadcastReceiver here    
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.MSG_ACTION);
    filter.addAction(UpdateIntent.SCHEDULER_CONNECTED_ACTION);
    this.registerReceiver(this.receiver, filter);
    this.consoleView = (ListView) this.findViewById(R.viewId.systemConsole);
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.unregisterReceiver(this.receiver);
  }
}

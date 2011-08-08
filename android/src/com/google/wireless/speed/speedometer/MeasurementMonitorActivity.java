// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class MeasurementMonitorActivity extends Activity {
  /** Called when the activity is first created. */
  
  private ListView consoleView;
  private ArrayAdapter<String> consoleContent;
  BroadcastReceiver receiver;
  
  public MeasurementMonitorActivity() {
    // This receiver only receives intent actions generated from UpdateIntent
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String msg = intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD);
        consoleContent.add(msg + "\n");
      }
    };
  }
   
  /* Upgrades the progress bar in the UI*/
  private void upgradeProgress(int progress, int max) {
      ProgressBar pb = (ProgressBar) this.findViewById(R.drawable.progress_bar);
      // Set it to be visible when updating
      pb.setVisibility(0);
      if (progress == max) {
        pb.setProgress(pb.getMax());
      } else {
        if (max > 0) {
          pb.setProgress(pb.getMax() * progress / max);
        }
      }
  }
  
  /* Catch the back button event and let the parent activity to decide what to do */
  @Override
  public void onBackPressed() {
    this.getParent().onBackPressed();   
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    // Register activity specific BroadcastReceiver here    
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.MSG_ACTION);
    this.registerReceiver(this.receiver, filter);
    consoleContent = new ArrayAdapter<String>(this, R.layout.list_item);
    this.consoleView = (ListView) this.findViewById(R.viewId.systemConsole);
    this.consoleView.setAdapter(consoleContent);
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.unregisterReceiver(this.receiver);
  }
}

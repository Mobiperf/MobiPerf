// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class MeasurementMonitorActivity extends Activity {
  /** Called when the activity is first created. */
  
  private TextView consoleView;
  BroadcastReceiver receiver;
  
  public MeasurementMonitorActivity() {
    // This receiver only receives intent actions generated from UpdateIntent
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String msg = intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD);
        consoleView.append(msg + "\n");
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
    filter.addAction(UpdateIntent.ACTION);
    this.registerReceiver(this.receiver, filter);
    this.consoleView = (TextView) this.findViewById(R.viewId.systemConsole);
    this.consoleView.setMovementMethod(new ScrollingMovementMethod());
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.unregisterReceiver(this.receiver);
  }
}

// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class UserTaskConsoleActivity extends Activity {
  /** Called when the activity is first created. */
  
  public static final String KEY_USER_CONSOLE_CONTENT = "KEY_USER_CONSOLE_CONTENT";
  public static final String TAB_TAG = "MY_MEASUREMENTS";
  
  private ListView consoleView;
  private ArrayAdapter<String> consoleContent;
  BroadcastReceiver receiver;
  ProgressBar progresBar;
  TextView statusBar;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.my_measurements);
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    consoleContent = new ArrayAdapter<String>(this, R.layout.list_item);
    // Restore saved content if it exists
    if (savedInstanceState != null) {
      ArrayList<String> savedContent = 
            savedInstanceState.getStringArrayList(KEY_USER_CONSOLE_CONTENT);
      if (savedContent != null) {
        for (String item : savedContent) {
          consoleContent.add(item);
        }
      }
    }
    this.consoleView = (ListView) this.findViewById(R.id.userConsole);
    this.consoleView.setAdapter(consoleContent);
    this.progresBar = (ProgressBar) this.findViewById(R.id.progress_bar);
    this.progresBar.setMax(Config.MAX_PROGRESS_BAR_VALUE);
    this.progresBar.setProgress(Config.MAX_PROGRESS_BAR_VALUE);
    this.statusBar = (TextView) this.findViewById(R.id.userConsoleStatusBar);
    
    this.receiver = new BroadcastReceiver() {
      @Override
      // All onXyz() callbacks are single threaded
      public void onReceive(Context context, Intent intent) {
        int priority = intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, 
            MeasurementTask.INVALID_PRIORITY);
        if (priority == MeasurementTask.USER_PRIORITY) {
          updateStatusBar(intent);
          insertStringToConsole(intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD));
          upgradeProgress(intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD,
              Config.INVALID_PROGRESS), Config.MAX_PROGRESS_BAR_VALUE);
        }
      }
    };
    this.registerReceiver(this.receiver, filter);
  }

  /**
   * Save the console content before onDestroy()
   * 
   * TODO(wenjiezeng): Android does not call onSaveInstanceState when the user
   * presses the 'back' button. To preserve console content between launches, we
   * need to write the content to persistent storage and restore it upon onCreate().
   */
  @Override
  protected void onSaveInstanceState(Bundle bundle) {
    int length = consoleContent.getCount();
    ArrayList<String> items = new ArrayList<String>();
    for (int i = 0; i < length; i++) {
      items.add(consoleContent.getItem(i));
    }
    bundle.putStringArrayList(KEY_USER_CONSOLE_CONTENT, items);
  }
  
  private void updateStatusBar(Intent intent) {
    String statusMsg = intent.getStringExtra(UpdateIntent.STATUS_MSG_PAYLOAD);
    if (statusMsg != null) {
      statusBar.setText(statusMsg);
    }
  }
  
  private void insertStringToConsole(String msg) {
    if (msg != null) {
      consoleContent.insert(msg + "\n", 0);
      if (consoleContent.getCount() > Config.MAX_LIST_ITEMS) {
        consoleContent.remove(consoleContent.getItem(consoleContent.getCount() - 1));
      }
    }
  }
  
  /**
   *  Upgrades the progress bar in the UI.
   *  */
  private void upgradeProgress(int progress, int max) {
    Log.i(SpeedometerApp.TAG, "Progress is " + progress);
    if (progress >= 0 && progress <= max) {
      progresBar.setProgress(progress);
      this.progresBar.setVisibility(View.VISIBLE);
    } else {
      /* UserMeasurementTask broadcast a progress greater than max to indicate the
       * termination of the measurement
       */
      this.progresBar.setVisibility(View.GONE);
    }
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.unregisterReceiver(this.receiver);
  }
}

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
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import java.util.ArrayList;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class ResultsConsoleActivity extends Activity {
  
  public static final String KEY_USER_CONSOLE_CONTENT = "KEY_USER_CONSOLE_CONTENT";
  public static final String TAB_TAG = "MY_MEASUREMENTS";
  
  private ListView consoleView;
  private ArrayAdapter<String> userResults;
  private ArrayAdapter<String> systemResults;
  BroadcastReceiver receiver;
  ProgressBar progresBar;
  boolean showUserResults = true;
  ToggleButton showUserResultButton;
  ToggleButton showSystemResultButton;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.results);
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    userResults = new ArrayAdapter<String>(this, R.layout.list_item);
    systemResults = new ArrayAdapter<String>(this, R.layout.list_item);
    // Restore saved content if it exists
    if (savedInstanceState != null) {
      ArrayList<String> savedContent = 
            savedInstanceState.getStringArrayList(KEY_USER_CONSOLE_CONTENT);
      if (savedContent != null) {
        for (String item : savedContent) {
          userResults.add(item);
        }
      }
    }
    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.consoleView.setAdapter(userResults);
    this.progresBar = (ProgressBar) this.findViewById(R.id.progress_bar);
    this.progresBar.setMax(Config.MAX_PROGRESS_BAR_VALUE);
    this.progresBar.setProgress(Config.MAX_PROGRESS_BAR_VALUE);
    showUserResultButton = (ToggleButton) findViewById(R.id.showUserResults);
    showSystemResultButton = (ToggleButton) findViewById(R.id.showSystemResults);
    showUserResultButton.setChecked(showUserResults);
    showSystemResultButton.setChecked(!showUserResults);
    
    // We enforce a either-or behavior between the two ToggleButtons
    showUserResultButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        updateConsole(showUserResults, isChecked);
        showUserResults = isChecked;
        showSystemResultButton.setChecked(!isChecked);
      }
    });
    showSystemResultButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        updateConsole(showUserResults, !isChecked);
        showUserResults = !isChecked;
        showUserResultButton.setChecked(!isChecked);
      }
    });
    
    this.receiver = new BroadcastReceiver() {
      @Override
      // All onXyz() callbacks are single threaded
      public void onReceive(Context context, Intent intent) {
        int progress = intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD, 
            Config.INVALID_PROGRESS);
        if (progress == Config.MEASUREMENT_END_PROGRESS) {
          int priority = intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, 
            MeasurementTask.INVALID_PRIORITY);
          if (priority == MeasurementTask.USER_PRIORITY) {
            insertStringToConsole(userResults, 
                intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD));
          } else {
            insertStringToConsole(systemResults, 
                intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD));
          }
        }
        upgradeProgress(progress, Config.MAX_PROGRESS_BAR_VALUE);
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
    int length = userResults.getCount();
    ArrayList<String> items = new ArrayList<String>();
    for (int i = 0; i < length; i++) {
      items.add(userResults.getItem(i));
    }
    bundle.putStringArrayList(KEY_USER_CONSOLE_CONTENT, items);
  }
  
  private void insertStringToConsole(ArrayAdapter<String> results, String msg) {
    if (msg != null) {
      results.insert(msg + "\n", 0);
      if (results.getCount() > Config.MAX_LIST_ITEMS) {
        results.remove(userResults.getItem(userResults.getCount() - 1));
      }
    }
  }
  
  /**
   * Change the underling adapter for the ListView depending on the old and new value 
   * of showUserResults. userResults is the ArraytAdapter that stores user results and
   * systemResults is the ArrayAdapter that stores system results. Depending on which one
   * is the underlying ArrayAdapter of consoleView, we see either user or system results.
   * 
   * @param oldShowUserResults the old value of showUserResults
   * @param newShowUserResults the new value of showUserResults
   */
  private void updateConsole(boolean oldShowUserResults, boolean newShowUserResults) {
    // No need to update if the old and new values are the same
    if (oldShowUserResults != newShowUserResults) {
      if (newShowUserResults) {
        this.consoleView.setAdapter(userResults);
      } else {
        this.consoleView.setAdapter(systemResults);
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

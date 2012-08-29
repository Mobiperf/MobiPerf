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
package com.mobiperf.speedometer;

import com.mobiperf.speedometer.R;

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

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class ResultsConsoleActivity extends Activity {
  
  public static final String TAB_TAG = "MY_MEASUREMENTS";
  
  private ListView consoleView;
  private ArrayAdapter<String> userResults;
  private ArrayAdapter<String> systemResults;
  BroadcastReceiver receiver;
  ProgressBar progressBar;
  ToggleButton showUserResultButton;
  ToggleButton showSystemResultButton;
  MeasurementScheduler scheduler = null;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Logger.d("ResultsConsoleActivity.onCreate called");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.results);
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.SCHEDULER_CONNECTED_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);

    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.progressBar = (ProgressBar) this.findViewById(R.id.progress_bar);
    this.progressBar.setMax(Config.MAX_PROGRESS_BAR_VALUE);
    this.progressBar.setProgress(Config.MAX_PROGRESS_BAR_VALUE);
    showUserResultButton = (ToggleButton) findViewById(R.id.showUserResults);
    showSystemResultButton = (ToggleButton) findViewById(R.id.showSystemResults);
    showUserResultButton.setChecked(true);
    showSystemResultButton.setChecked(false);
    
    // We enforce a either-or behavior between the two ToggleButtons
    OnCheckedChangeListener buttonClickListener = new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == showUserResultButton) {
          switchBetweenResults(isChecked);
        } else {
          switchBetweenResults(!isChecked);
        }
      }};
    showUserResultButton.setOnCheckedChangeListener(buttonClickListener);
    showSystemResultButton.setOnCheckedChangeListener(buttonClickListener);
    
    this.receiver = new BroadcastReceiver() {
      @Override
      // All onXyz() callbacks are single threaded
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION)) {
          int progress = intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD, 
              Config.INVALID_PROGRESS);
          int priority = intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, 
              MeasurementTask.INVALID_PRIORITY);
          // Show user results if we there is currently a user measurement running
          if (priority == MeasurementTask.USER_PRIORITY) {
            switchBetweenResults(true);
          }
          upgradeProgress(progress, Config.MAX_PROGRESS_BAR_VALUE);
        }
        getConsoleContentFromScheduler();
      }
    };
    this.registerReceiver(this.receiver, filter);
    
    getConsoleContentFromScheduler();
  }
  
  /**
   * Change the underlying adapter for the ListView.
   * 
   * @param showUserResults If true, show user results; otherwise, show system results.
   */
  private void switchBetweenResults(boolean showUserResults) {
    getConsoleContentFromScheduler();
    showUserResultButton.setChecked(showUserResults);
    showSystemResultButton.setChecked(!showUserResults);
    if (showUserResults) {
      Logger.d("switchBetweenResults: showing " + userResults.getCount() + " user results");
      this.consoleView.setAdapter(userResults);
    } else {
      Logger.d("switchBetweenResults: showing " + systemResults.getCount() + " system results");
      this.consoleView.setAdapter(systemResults);
    }
  }
  
  /**
   *  Upgrades the progress bar in the UI.
   *  */
  private void upgradeProgress(int progress, int max) {
    Logger.d("Progress is " + progress);
    if (progress >= 0 && progress <= max) {
      progressBar.setProgress(progress);
      this.progressBar.setVisibility(View.VISIBLE);
    } else {
      /* UserMeasurementTask broadcast a progress greater than max to indicate the
       * termination of the measurement
       */
      this.progressBar.setVisibility(View.INVISIBLE);
    }
  }
  
  @Override
  protected void onDestroy() {
    Logger.d("ResultsConsoleActivity.onDestroy called");
    super.onDestroy();
    this.unregisterReceiver(this.receiver);
  }
  
  private synchronized void getConsoleContentFromScheduler() {
    Logger.d("ResultsConsoleActivity.getConsoleContentFromScheduler called");
    if (scheduler == null) {
      SpeedometerApp parent = (SpeedometerApp) getParent();
      scheduler = parent.getScheduler();
    }
    if (scheduler != null) {
      // In case scheduler has not started yet.
      userResults = new ArrayAdapter<String>(this, R.layout.list_item,
          scheduler.getUserResults());
      systemResults = new ArrayAdapter<String>(this, R.layout.list_item,
          scheduler.getSystemResults());
    }
  }
}

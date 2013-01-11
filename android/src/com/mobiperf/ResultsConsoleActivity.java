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
package com.mobiperf;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ToggleButton;

import java.util.List;

import com.mobiperf.R;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 */
public class ResultsConsoleActivity extends Activity {
  
  public static final String TAB_TAG = "MY_MEASUREMENTS";
  
  private ListView consoleView;
  private ArrayAdapter<String> results;
  BroadcastReceiver receiver;
  ProgressBar progressBar;
  ToggleButton showUserResultButton;
  ToggleButton showSystemResultButton;
  MeasurementScheduler scheduler = null;
  boolean userResultsActive = false;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Logger.d("ResultsConsoleActivity.onCreate called");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.results);
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.SCHEDULER_CONNECTED_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);

    this.consoleView = (ListView) this.findViewById(R.id.resultConsole);
    this.results = new ArrayAdapter<String>(getApplicationContext(), R.layout.list_item);
    this.consoleView.setAdapter(this.results);
    this.progressBar = (ProgressBar) this.findViewById(R.id.progress_bar);
    this.progressBar.setMax(Config.MAX_PROGRESS_BAR_VALUE);
    this.progressBar.setProgress(Config.MAX_PROGRESS_BAR_VALUE);
    showUserResultButton = (ToggleButton) findViewById(R.id.showUserResults);
    showSystemResultButton = (ToggleButton) findViewById(R.id.showSystemResults);
    showUserResultButton.setChecked(true);
    showSystemResultButton.setChecked(false);
    userResultsActive = true;
    
    // We enforce a either-or behavior between the two ToggleButtons
    OnCheckedChangeListener buttonClickListener = new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        Logger.d("onCheckedChanged");
        switchBetweenResults(buttonView == showUserResultButton ? isChecked : !isChecked);
      }
    };
    showUserResultButton.setOnCheckedChangeListener(buttonClickListener);
    showSystemResultButton.setOnCheckedChangeListener(buttonClickListener);
    
    this.receiver = new BroadcastReceiver() {
      @Override
      // All onXyz() callbacks are single threaded
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION)) {
          int progress = intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD, Config.INVALID_PROGRESS);
          int priority = intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, 
              MeasurementTask.INVALID_PRIORITY);
          // Show user results if there is currently a user measurement running
          if (priority == MeasurementTask.USER_PRIORITY) {
            Logger.d("progress update");
            switchBetweenResults(true);
          }
          upgradeProgress(progress, Config.MAX_PROGRESS_BAR_VALUE);
        } else if (intent.getAction().equals(UpdateIntent.SCHEDULER_CONNECTED_ACTION)) {
          Logger.d("scheduler connected");
          switchBetweenResults(userResultsActive);
        }
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
  private synchronized void switchBetweenResults(boolean showUserResults) {
    userResultsActive = showUserResults;
    getConsoleContentFromScheduler();
    showUserResultButton.setChecked(showUserResults);
    showSystemResultButton.setChecked(!showUserResults);
    Logger.d("switchBetweenResults: showing " + results.getCount() + " " +
             (showUserResults ? "user" : "system") + " results");
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
      // UserMeasurementTask broadcast a progress greater than max to indicate the termination of
      // the measurement.
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
    // Scheduler may have not had time to start yet. When it does, the intent above will call this
    // again.
    if (scheduler != null) {
      Logger.d("Updating measurement results from thread " + Thread.currentThread().getName());
      results.clear();
      final List<String> scheduler_results =
          (userResultsActive ? scheduler.getUserResults() : scheduler.getSystemResults());
      for (String result : scheduler_results) {
        results.add(result);
      }
      runOnUiThread(new Runnable() {
        public void run() { results.notifyDataSetChanged(); }
      });
    }
  }
}

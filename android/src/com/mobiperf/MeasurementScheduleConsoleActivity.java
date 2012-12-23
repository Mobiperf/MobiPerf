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
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.AbstractCollection;
import java.util.Date;
import java.util.HashMap;

import com.mobiperf.R;

/**
 * Activity that shows the current measurement schedule of the scheduler
 */
public class MeasurementScheduleConsoleActivity extends Activity {
  public static final String TAB_TAG = "MEASUREMENT_SCHEDULE";
  
  private MeasurementScheduler scheduler;
  private SpeedometerApp parent;
  private ListView consoleView;
  private TextView lastCheckinTimeText;
  private ArrayAdapter<String> consoleContent;
  // Maps the toString() of a measurementTask to its key
  private HashMap<String, String> taskMap;
  private int longClickedItemPosition = -1;
  private BroadcastReceiver receiver;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.measurement_schedule);
    
    taskMap = new HashMap<String, String>();
    parent = (SpeedometerApp) this.getParent();
    consoleContent = new ArrayAdapter<String>(this, R.layout.list_item);
    this.consoleView = (ListView) this.findViewById(R.id.measurementScheduleConsole);
    this.consoleView.setAdapter(consoleContent);
    lastCheckinTimeText = (TextView)this.findViewById(R.id.lastCheckinTime);
    Button checkinButton = (Button) this.findViewById(R.id.checkinButton);
    checkinButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        doCheckin();
      }
    });

    registerForContextMenu(consoleView);
    consoleView.setOnItemLongClickListener(new OnItemLongClickListener() {
      /**
       * Records which item in the list is selected
       */
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        longClickedItemPosition = position;
        return false;
      }
    });
    
    // Register activity specific BroadcastReceiver here    
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.SCHEDULER_CONNECTED_ACTION);
    filter.addAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        Logger.d("MeasurementConsole got intent");
        /* The content of the console is maintained by the scheduler. We simply hook up the 
         * view with the content here. */
        updateConsole();
      }
    };
    registerReceiver(receiver, filter);
  }
  
  /**
   * Handles context menu creation for the ListView in the console
   */
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
                                  ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.scheduler_console_context_menu, menu);
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    updateConsole();
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    unregisterReceiver(receiver);
  }
  
  /**
   * Handles the deletion of the measurement tasks when the user clicks the context menu
   */
  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    switch (item.getItemId()) {
      case R.id.ctxMenuDeleteTask:
        scheduler = parent.getScheduler();
        if (scheduler != null) {
          String selectedTaskString = consoleContent.getItem(longClickedItemPosition);
          String taskKey = taskMap.get(selectedTaskString);
          if (taskKey != null) {
            scheduler.removeTaskByKey(taskKey);
          }
        }
        updateConsole();
        return true;
      default:
    }
    return false;
  }
  
  private void updateLastCheckinTime() {
    Logger.i("updateLastCheckinTime() called");
    scheduler = parent.getScheduler();
    if (scheduler != null) {
      Date lastCheckin = scheduler.getLastCheckinTime();
      if (lastCheckin != null) {
        lastCheckinTimeText.setText("Last checkin " + lastCheckin);
      } else {
        lastCheckinTimeText.setText("No checkins yet");
      }
    }
  }
  
  private void updateConsole() {
    Logger.i("updateConsole() called");
    scheduler = parent.getScheduler();
    if (scheduler != null) {
      AbstractCollection<MeasurementTask> tasks = scheduler.getTaskQueue();
      consoleContent.clear();
      taskMap.clear();
      for (MeasurementTask task : tasks) {
        String taskStr = task.toString();
        consoleContent.add(taskStr);
        taskMap.put(taskStr, task.getDescription().key);
      }
    }
    updateLastCheckinTime();
  }
  
  private void doCheckin() {
    Logger.i("doCheckin() called");
    scheduler = parent.getScheduler();
    if (scheduler != null) {
      lastCheckinTimeText.setText("Checking in...");
      scheduler.handleCheckin(true);
    }
  }
  
}

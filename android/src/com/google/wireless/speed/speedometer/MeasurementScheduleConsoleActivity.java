// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

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

import java.util.AbstractCollection;
import java.util.HashMap;

/**
 * Activity that shows the current measurement schedule of the scheduler
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class MeasurementScheduleConsoleActivity extends Activity {
  public static final String TAB_TAG = "MEASUREMENT_SCHEDULE";
  
  private MeasurementScheduler scheduler;
  private SpeedometerApp parent;
  private ListView consoleView;
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
    Button refreshButton = (Button) this.findViewById(R.id.refreshScheduleButton);
    refreshButton.setOnClickListener(new OnClickListener() {
      /**
       * Updates the schedule in the console
       */
      @Override
      public void onClick(View v) {
        updateConsole();
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
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
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
  
  private void updateConsole() {
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
  }
}

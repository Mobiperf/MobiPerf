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

import java.util.ArrayList;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class MeasurementMonitorActivity extends Activity {
  /** Called when the activity is first created. */
  
  public static final String KEY_CONSOLE_CONTENT = "KEY_CONSOLE_CONTENT";
  public static final String TAB_TAG = "MEASUREMENT_MONITOR";
  
  private ListView consoleView;
  private ArrayAdapter<String> consoleContent;
  BroadcastReceiver receiver;
  
  public MeasurementMonitorActivity() {
    // This receiver only receives intent actions generated from UpdateIntent
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String msg = intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD);
        // All onXyz() callbacks are single threaded
        consoleContent.insert(msg + "\n", 0);
        if (consoleContent.getCount() > Config.MAX_LIST_ITEMS) {
          consoleContent.remove(consoleContent.getItem(consoleContent.getCount() - 1));
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
    this.registerReceiver(this.receiver, filter);
    consoleContent = new ArrayAdapter<String>(this, R.layout.list_item);
    // Restore saved content if it exists
    if (savedInstanceState != null) {
      ArrayList<String> savedContent = savedInstanceState.getStringArrayList(KEY_CONSOLE_CONTENT);
      if (savedContent != null) {
        for (String item : savedContent) {
          consoleContent.add(item);
        }
      }
    }
    this.consoleView = (ListView) this.findViewById(R.viewId.systemConsole);
    this.consoleView.setAdapter(consoleContent);
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
    bundle.putStringArrayList(KEY_CONSOLE_CONTENT, items);
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.unregisterReceiver(this.receiver);
  }
}

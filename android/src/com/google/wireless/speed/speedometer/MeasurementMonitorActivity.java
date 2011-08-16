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
  
  private ListView consoleView;
  private ArrayAdapter<String> consoleContent;
  BroadcastReceiver receiver;
  
  public MeasurementMonitorActivity() {
    // This receiver only receives intent actions generated from UpdateIntent
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String msg = intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD);
        synchronized (consoleContent) {
          consoleContent.add(msg + "\n");
          if (consoleContent.getCount() > Config.MAX_LIST_ITEMS) {
            refreshConsole();
          }
        }
      }
    };
  }
  
  /**
   * Keep only the last ITEMS_TO_KEEP_UPON_REFRESH items in the console and remove the rest. 
   */
  private void refreshConsole() {
    ArrayList<String> latestItems = new ArrayList<String>();
    synchronized (consoleContent) {
      int length = consoleContent.getCount();
      int copyStart = length - Config.ITEMS_TO_KEEP_UPON_REFRESH;
      for (int i = copyStart; i < length; i++) {
        latestItems.add(consoleContent.getItem(i)); 
      }
      length = latestItems.size();
      consoleContent.clear();
      for (int i = 0; i < length; i++) {
        consoleContent.add(latestItems.get(i));
      }
    }
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
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
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

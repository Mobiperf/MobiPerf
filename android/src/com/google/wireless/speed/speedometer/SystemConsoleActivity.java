// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import com.google.myjson.reflect.TypeToken;
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.lang.reflect.Type;
import java.util.ArrayList;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class SystemConsoleActivity extends Activity {
  /** Called when the activity is first created. */
  public static final String TAB_TAG = "MEASUREMENT_MONITOR";
  
  private ListView consoleView;
  private ArrayAdapter<String> consoleContent;
  BroadcastReceiver receiver;
  
  public SystemConsoleActivity() {
    // This receiver only receives intent actions generated from UpdateIntent
    this.receiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String msg = intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD);
        // All onXyz() callbacks are single threaded
        insertToConsole(msg);
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
    restoreConsole();
    this.consoleView = (ListView) this.findViewById(R.viewId.systemConsole);
    this.consoleView.setAdapter(consoleContent);
  }
  
  private void insertToConsole(String msg) {
    if (msg != null) {
      consoleContent.insert(msg, 0);
      if (consoleContent.getCount() > Config.MAX_LIST_ITEMS) {
        consoleContent.remove(consoleContent.getItem(consoleContent.getCount() - 1));
      }
    }
  }
  
  /**
   * Persists the content of the console as a JSON string
   */
  private void saveConsoleContent() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    SharedPreferences.Editor editor = prefs.edit();

    int length = consoleContent.getCount();
    ArrayList<String> items = new ArrayList<String>();
    // Since we use insertToConsole later on to restore the content, we have to store them
    // in the reverse order to maintain the same look
    for (int i = length - 1; i >= 0; i--) {
      items.add(consoleContent.getItem(i));
    }
    Type listType = new TypeToken<ArrayList<String>>(){}.getType();
    editor.putString(Config.PREF_KEY_SYSTEM_CONSOLE, 
        MeasurementJsonConvertor.getGsonInstance().toJson(items, listType));
    editor.commit();
  }
  
  /**
   * Restores the console content from the saved JSON string
   */
  private void restoreConsole() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String savedConsole = prefs.getString(Config.PREF_KEY_SYSTEM_CONSOLE, 
        null);
    if (savedConsole != null) {
      Type listType = new TypeToken<ArrayList<String>>(){}.getType();
      ArrayList<String> items = MeasurementJsonConvertor.getGsonInstance().fromJson(savedConsole, 
          listType);
      if (items != null) {
        for (String item : items) {
          insertToConsole(item);
        }
      }
    }
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.unregisterReceiver(this.receiver);
    saveConsoleContent();
  }
}

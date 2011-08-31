// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.MeasurementScheduler.SchedulerBinder;

import android.app.TabActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;
import android.widget.TextView;

import java.security.Security;

/**
 * The main UI thread that manages different tabs
 * TODO(Wenjie): Implement button handler to stop all threads and the Speedometer app
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class SpeedometerApp extends TabActivity {
  
  public static final String TAG = "Speedometer";
  
  private MeasurementScheduler scheduler;
  private TabHost tabHost;
  private boolean isBound = false;
  private boolean isBindingToService = false;
  private BroadcastReceiver receiver;
  TextView statusBar;
  
  /** Defines callbacks for service binding, passed to bindService() */
  private ServiceConnection serviceConn = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService
      // instance
      SchedulerBinder binder = (SchedulerBinder) service;
      scheduler = binder.getService();
      isBound = true;
      isBindingToService = false;
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      isBound = false;
    }
  };
  
  /** Returns the scheduler singleton instance. Should only be called from the UI thread. */
  public MeasurementScheduler getScheduler() {
    if (isBound) {
      return this.scheduler;
    } else {
      bindToService();
      return null;
    }
  }
  
  /** Returns the tab host. Allows child tabs to request focus changes, etc... */
  public TabHost getSpeedomterTabHost() {
    return tabHost;
  }
  
  private void setPauseIconBasedOnSchedulerState(MenuItem item) {
    if (this.scheduler != null && item != null) {
      if (this.scheduler.isPauseRequested()) {
        item.setIcon(android.R.drawable.ic_media_play);
        item.setTitle(R.string.menuResume);
      } else {
        item.setIcon(android.R.drawable.ic_media_pause);
        item.setTitle(R.string.menumPause);
      }
    }
  }
  
  /** Populate the application menu. Only called once per onCreate() */
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return true;
  }
  
  /** Adjust menu items depending on system state. Called every time the 
   *  menu pops up */
  @Override
  public boolean onPrepareOptionsMenu (Menu menu) {
    setPauseIconBasedOnSchedulerState(menu.findItem(R.id.menuPauseResume));
    return true;
  }
  
  /** React to menu item selections */
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle item selection
    switch (item.getItemId()) {
      case R.id.menuPauseResume:
        if (this.scheduler != null) {
          if (this.scheduler.isPauseRequested()) {
            this.scheduler.resume();
            updateStatusBar(SpeedometerApp.this.getString(R.string.resumeMessage));
          } else {
            this.scheduler.pause();
            updateStatusBar(SpeedometerApp.this.getString(R.string.pauseMessage));
          }
        }
        return true;
      case R.id.menuQuit:
        Log.i(TAG, "User requests exit. Quiting the app");
        quitApp();
        return true;
      case R.id.menuSettings:
        Intent settingsActivity = new Intent(getBaseContext(),
            SpeedometerPreferenceActivity.class);
        startActivity(settingsActivity);
        return true;
      case R.id.aboutPage:
        Intent intent = new Intent(getBaseContext(),
            AboutActivity.class);
        startActivity(intent);
        return true;  
      default:
        return super.onOptionsItemSelected(item);
    }
  }
    
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    
    /* Set the DNS cache TTL to 0 such that measurements can be more accurate.
     * However, it is known that the current Android OS does not take actions
     * on these properties but may enforce them in future versions.
     */ 
    System.setProperty("networkaddress.cache.ttl", "0");
    System.setProperty("networkaddress.cache.negative.ttl", "0");
    Security.setProperty("networkaddress.cache.ttl", "0");
    Security.setProperty("networkaddress.cache.negative.ttl", "0"); 

    Resources res = getResources(); // Resource object to get Drawables
    tabHost = getTabHost();  // The activity TabHost
    TabHost.TabSpec spec;  // Resusable TabSpec for each tab
    Intent intent;  // Reusable Intent for each tab

    // Create an Intent to launch an Activity for the tab (to be reused)
    intent = new Intent().setClass(this, MeasurementMonitorActivity.class);

    // Initialize a TabSpec for each tab and add it to the TabHost
    spec = tabHost.newTabSpec(MeasurementMonitorActivity.TAB_TAG).setIndicator(
        "Console").setContent(intent);
    tabHost.addTab(spec);

    // Do the same for the other tabs
    intent = new Intent().setClass(this, MeasurementCreationActivity.class);
    spec = tabHost.newTabSpec(MeasurementCreationActivity.TAB_TAG).setIndicator(
        "Measure").setContent(intent);
    tabHost.addTab(spec);
    // Creates the user task console tab
    intent = new Intent().setClass(this, UserTaskConsoleActivity.class);
    spec = tabHost.newTabSpec(UserTaskConsoleActivity.TAB_TAG).setIndicator(
        "Results").setContent(intent);
    tabHost.addTab(spec);

    tabHost.setCurrentTabByTag(MeasurementMonitorActivity.TAB_TAG);
    
    statusBar = (TextView) findViewById(R.id.systemStatusBar);
    
    // We only need one instance of scheduler thread
    intent = new Intent(this, MeasurementScheduler.class);
    this.startService(intent);
    // Bind to the scheduler service for only once during the lifetime of the activity
    bindToService();
    
    this.receiver = new BroadcastReceiver() {
      @Override
      // All onXyz() callbacks are single threaded
      public void onReceive(Context context, Intent intent) {
        String statusMsg = intent.getStringExtra(UpdateIntent.STATUS_MSG_PAYLOAD);
        updateStatusBar(statusMsg);
      }
    };
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
    this.registerReceiver(this.receiver, filter);
  }
  
  private void updateStatusBar(String statusMsg) {
    if (statusMsg != null) {
      statusBar.setText(statusMsg);
    }
  }
  
  private void bindToService() {
    if (!isBindingToService && !isBound) {
      // Bind to the scheduler service if it is not bounded
      Intent intent = new Intent(this, MeasurementScheduler.class);
      bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
      isBindingToService = true;
    }
  }
  
  @Override
  protected void onStart() {
    super.onStart();
  }
  
  @Override
  protected void onStop() {
    super.onStop();
    if (isBound) {
      unbindService(serviceConn);
      isBound = false;
    }    
  }

  
  private void quitApp() {
    if (isBound) {
      unbindService(serviceConn);
      isBound = false;
    }
    if (this.scheduler != null) {
      scheduler.requestStop();
    }
    this.finish();
    System.exit(0);
  }
}

// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.MeasurementScheduler.SchedulerBinder;
import com.google.wireless.speed.speedometer.util.RuntimeUtil;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.TabActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TabHost;

import java.security.Security;

/**
 * The main UI thread that manages different tabs
 * TODO(Wenjie): Implement button handler to stop all threads and the Speedometer app
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class SpeedometerApp extends TabActivity {
  
  public static final String TAG = "Speedometer";
  private static final int NOTIFICATION_ID = 1234;
  
  private MeasurementScheduler scheduler;
  private boolean isBounded = false;  
  /** Defines callbacks for service binding, passed to bindService() */
  private ServiceConnection serviceConn = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService
      // instance
      SchedulerBinder binder = (SchedulerBinder) service;
      scheduler = binder.getService();
      //The intent to launch when the user clicks the expanded notification
      Intent intent = new Intent(SpeedometerApp.this, MeasurementScheduler.class);
      PendingIntent pendIntent = PendingIntent.getService(SpeedometerApp.this, 0, intent, 
          PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);

      //This constructor is deprecated. Use Notification.Builder instead
      Notification notice = new Notification(R.drawable.icon, 
          getString(R.string.notificationSchedulerStarted), System.currentTimeMillis());

      //This method is deprecated. Use Notification.Builder instead.
      notice.setLatestEventInfo(SpeedometerApp.this, "Speedometer", 
          getString(R.string.notificatioContent), pendIntent);

      notice.flags |= Notification.FLAG_NO_CLEAR;
      scheduler.startForeground(NOTIFICATION_ID, notice);
      isBounded = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      isBounded = false;
    }
  };
  
  /** Returns the scheduler singleton instance */
  public MeasurementScheduler getScheduler() {
    if (isBounded) {
      return this.scheduler;
    } else {
      return null;
    }
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
          } else {
            this.scheduler.pause();
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
    TabHost tabHost = getTabHost();  // The activity TabHost
    TabHost.TabSpec spec;  // Resusable TabSpec for each tab
    Intent intent;  // Reusable Intent for each tab

    // Create an Intent to launch an Activity for the tab (to be reused)
    intent = new Intent().setClass(this, MeasurementMonitorActivity.class);

    // Initialize a TabSpec for each tab and add it to the TabHost
    spec = tabHost.newTabSpec("measurement_monitor").setIndicator("Console",
        res.getDrawable(R.drawable.tablet)).setContent(intent);
    tabHost.addTab(spec);

    // Do the same for the other tabs
    intent = new Intent().setClass(this, MeasurementCreationActivity.class);
    spec = tabHost.newTabSpec("measurement_creation").setIndicator("Create Measurement",
        res.getDrawable(R.drawable.tablet)).setContent(intent);
    tabHost.addTab(spec);

    tabHost.setCurrentTab(0);
    
    RuntimeUtil.setActivity(this);
    
 // We only need one instance of scheduler thread
    Intent schedulerIntent = new Intent(this, MeasurementScheduler.class);
    this.startService(schedulerIntent);
    // Bind to LocalService
    Intent bindIntent = new Intent(this, MeasurementScheduler.class);
    bindService(bindIntent, serviceConn, Context.BIND_AUTO_CREATE);
  }
  
  @Override
  protected void onStart() {
    super.onStart();
  }
  
  @Override
  protected void onStop() {
    super.onStop();
    if (isBounded) {
      unbindService(serviceConn);
      isBounded = false;
    }
  }
  
  private void quitApp() {
    if (this.scheduler != null) {
      scheduler.requestStop();
    }
    this.finish();
    System.exit(0);
  }
}

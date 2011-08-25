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
  // This arbitrary id is private to Speedometer
  private static final int NOTIFICATION_ID = 1234;
  
  private MeasurementScheduler scheduler;
  private boolean isBound = false;
  private boolean isBindingToService = false;  
  /** Defines callbacks for service binding, passed to bindService() */
  private ServiceConnection serviceConn = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService
      // instance
      SchedulerBinder binder = (SchedulerBinder) service;
      scheduler = binder.getService();
      //The intent to launch when the user clicks the expanded notification
      Intent intent = new Intent(SpeedometerApp.this, SpeedometerApp.class);
      PendingIntent pendIntent = PendingIntent.getActivity(SpeedometerApp.this, 0, intent, 
          PendingIntent.FLAG_CANCEL_CURRENT);

      //This constructor is deprecated in 3.x. But most phones still run 2.x systems
      Notification notice = new Notification(R.drawable.icon, 
          getString(R.string.notificationSchedulerStarted), System.currentTimeMillis());

      //This is deprecated in 3.x. But most phones still run 2.x systems
      notice.setLatestEventInfo(SpeedometerApp.this, "Speedometer", 
          getString(R.string.notificatioContent), pendIntent);

      //Put scheduler service into foreground. Makes the process less likely of being killed
      scheduler.startForeground(NOTIFICATION_ID, notice);
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
    intent = new Intent(this, MeasurementScheduler.class);
    this.startService(intent);
    // Bind to the scheduler service for only once during the lifetime of the activity
    bindToService();
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
    if (this.scheduler != null) {
      scheduler.requestStop();
    }
    this.finish();
    System.exit(0);
  }
}

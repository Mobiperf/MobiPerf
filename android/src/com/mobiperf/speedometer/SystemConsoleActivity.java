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
import com.mobiperf.speedometer.MeasurementScheduler.SchedulerBinder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * The activity that provides a console and progress bar of the ongoing measurement
 */
public class SystemConsoleActivity extends Activity {
  private ListView consoleView;
  private MeasurementScheduler scheduler = null;
  private boolean isBound = false;
  
  /** Defines callbacks for service binding, passed to bindService() */
  private ServiceConnection serviceConn = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService
      // instance
      SchedulerBinder binder = (SchedulerBinder) service;
      scheduler = binder.getService();
      isBound = true;
      updateConsole();
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      isBound = false;
    }
  };
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.console);
    this.consoleView = (ListView) this.findViewById(R.viewId.systemConsole);
    bindToService();
  }
  
  @Override
  protected void onResume() {
    super.onResume();
    updateConsole();
  }
  
  private void bindToService() {
    if (!isBound) {
      // Bind to the scheduler service if it is not bounded
      Intent intent = new Intent(this, MeasurementScheduler.class);
      bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
    }
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (isBound) {
      unbindService(serviceConn);
      isBound = false;
    }
  }
  
  private void updateConsole() {
    Logger.d("Updating system console from thread " + Thread.currentThread().getName());
    if (scheduler != null) {
      consoleView.setAdapter(new ArrayAdapter<String>(
          this, R.layout.list_item, scheduler.getSystemConsole()));
    }
  }
}

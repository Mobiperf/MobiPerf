/* Copyright 2012 University of Michigan.
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
package com.mobiperf.mobiperf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.mobiperf.speedometer.AccountSelector;
import com.mobiperf.speedometer.MeasurementCreationActivity;
import com.mobiperf.speedometer.MeasurementScheduleConsoleActivity;
import com.mobiperf.speedometer.MeasurementScheduler;
import com.mobiperf.speedometer.MeasurementScheduler.SchedulerBinder;
import com.mobiperf.speedometer.ResultsConsoleActivity;
import com.mobiperf.speedometer.SystemConsoleActivity;

/**
 * Home screen for Mobiperf which hosts all the different activities.  
 * Contains the measurement scheduler for managing measurement tasks.
 * 
 * @author hjx@umich.edu (Junxian Huang)
 */
public class MobiperfActivity extends Activity {

	// Define menu ids
	protected static final int MENU_PERIODIC = Menu.FIRST;
	protected static final int MENU_EMAIL = Menu.FIRST + 4;
	protected static final int PAST_RECORD = Menu.FIRST + 5;
	protected static final int PERF_ME = Menu.FIRST + 7;

	// Define dialog id
	protected static final int DIALOG_AGREEMENT = 1;
	protected static final int DIALOG_ACCOUNT_SELECTOR = 2;

	public static MeasurementScheduler scheduler;
	private boolean isBound = false;
	private boolean isBindingToService = false;

	/** 
	 * Defines callbacks for service binding, passed to bindService() 
	 */

	private ServiceConnection serviceConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			SchedulerBinder binder = (SchedulerBinder) service;
			scheduler = binder.getService();
			isBound = true;
			isBindingToService = false;
			// initializeStatusBar();
			// HomeScreen.this.sendBroadcast(new UpdateIntent("", UpdateIntent.SCHEDULER_CONNECTED_ACTION));
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			isBound = false;
		}
	};

	private void bindToService() {
		if (!isBindingToService && !isBound) {
			// Bind to the scheduler service if it is not bounded
			Intent intent = new Intent(this, MeasurementScheduler.class);
			bindService(intent, serviceConn, Context.BIND_AUTO_CREATE);
			isBindingToService = true;
		}
	}

	protected void onStart() {
		// Bind to the scheduler service for only once during the lifetime of
		// the activity
		bindToService();
		super.onStart();

		checkFirstTimeRun();
	}

	protected void onStop() {
		super.onStop();
		if (isBound) {
			unbindService(serviceConn);
			isBound = false;
		}
	}

	public MeasurementScheduler getScheduler() {
		if (isBound) {
			return MobiperfActivity.scheduler;
		} else {
			bindToService();
			return null;
		}
	}

	// Create menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	// Deal with menu event
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.v("menu", "onOptionsItemSelected " + item.getItemId());

		switch (item.getItemId()) {
		case R.id.menuPauseResume:
			if (MobiperfActivity.scheduler != null) {
				if (MobiperfActivity.scheduler.isPauseRequested()) {
					MobiperfActivity.scheduler.resume();
				} else {
					MobiperfActivity.scheduler.pause();
				}
			}
			return true;
		case R.id.menuQuit:
			quitApp();
			return true;
		case R.id.menuSettings:
			Intent settingsActivity = new Intent(getBaseContext(),
					com.mobiperf.mobiperf.Preferences.class);
			startActivity(settingsActivity);
			return true;
		case R.id.aboutPage:
			Intent intent = new Intent(getBaseContext(),
					com.mobiperf.mobiperf.About.class);
			startActivity(intent);
			return true;
		case R.id.menuLog:
			intent = new Intent(getBaseContext(), SystemConsoleActivity.class);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	//pop up dialog
	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		AlertDialog.Builder builder;
		switch(id) {
		case DIALOG_AGREEMENT:
			builder = new AlertDialog.Builder(this);
			builder.setTitle("Terms and Agreement")
			.setMessage(R.string.terms)
			.setCancelable(false)
			.setPositiveButton("Agree", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					MobiperfActivity.this.dismissDialog(DIALOG_AGREEMENT);
					Utilities.writeToFile(getFirstTimeMarkFileName(), Context.MODE_PRIVATE, "ok", MobiperfActivity.this);
				}
			}).setNegativeButton("Quit", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					MobiperfActivity.this.quitApp();
				}
			});
			dialog = builder.create();
			break;
		case DIALOG_ACCOUNT_SELECTOR:
			builder = new AlertDialog.Builder(this);
			builder.setTitle("Select Authentication Account");
			final CharSequence[] items = AccountSelector.getAccountList(this.getApplicationContext());
			if(items == null){
				Toast.makeText(getApplicationContext(), "There is no Google account connected for MobiPerf authentication, you may try again once you add your account to this phone.", Toast.LENGTH_SHORT).show();
				dialog = null;
				break;
			}
			builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
					
					//TODO: pass the selected account to AccountSelector via file/Intent?
				}
			});
			AlertDialog alert = builder.create();
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}
		return dialog;
	}

	//check whether this is the first time run
	private void checkFirstTimeRun() {

		showDialog(DIALOG_ACCOUNT_SELECTOR);

		String fileName = getFirstTimeMarkFileName();
		String[] fileList = fileList();
		for(int i = 0; i < fileList.length; i++)
			if(fileList[i].equals(fileName))
				// Already shown up
				return;
		// The following codes will only be called once after the app is installed and 
		// opened for the first time
		showDialog(DIALOG_AGREEMENT);
	}

	//get the file name of the first time mark file
	private String getFirstTimeMarkFileName(){
		String fileName = "first_time_mark_";
		try {
			PackageManager manager = this.getPackageManager();	
			PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
			fileName += this.getPackageName() + "_" + info.versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return fileName;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.home);

		findViewById(R.id.home_btn_mtask).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(getActivity(),
								MeasurementCreationActivity.class));
					}
				});

		findViewById(R.id.home_btn_results).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(getActivity(),
								ResultsConsoleActivity.class));
					}
				});

		findViewById(R.id.home_btn_networktoggle).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(getActivity(),
								NetworkToggle.class));
					}
				});

		findViewById(R.id.home_btn_about).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(getActivity(),
								com.mobiperf.mobiperf.About.class));
					}
				});

		findViewById(R.id.home_btn_settings).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(getActivity(),
								Preferences.class));
					}
				});

		findViewById(R.id.home_btn_taskqueue).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						startActivity(new Intent(getActivity(),
								MeasurementScheduleConsoleActivity.class));
					}
				});

		this.startService(new Intent(this, MeasurementScheduler.class));
	}

	private void quitApp() {
		if (isBound) {
			//TODO Junxian: why comment out the following line?
			//unbindService(serviceConn);
			isBound = false;
		}
		if (MobiperfActivity.scheduler != null) {
			scheduler.requestStop();
		}
		this.finish();
		System.exit(0);
	}

	protected Activity getActivity() {
		return this;
	}

}

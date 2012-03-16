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

import com.mobiperf.mobiperf.TimeSetting;
import com.mobiperf.mobiperf.R;
import com.mobiperf.speedometer.speed.Config;
import com.mobiperf.speedometer.speed.Logger;
import com.mobiperf.speedometer.speed.PeriodicTest;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/*
 * Preference activity allowing user to enable/disable various settings.
 */
public class Preferences extends PreferenceActivity {
	public static final int NOTIFICATION_ID = 0;
	private static boolean isNotificationEnabled = true; // Show notification by
															// default


	//
	// Define dialog ids

	protected static final int DIALOG_PERIODIC = 0;
	public static final int DIALOG_WARNING = 1;
	protected static final int DIALOG_NOTIFICATION = 2;
	protected static final String WARNING = "NAT and firewall tests require root access to open raw socket. "
			+ "Other tests still run normally if your phone is not rooted.\n\n"
			+ "A very lightweight test is run periodically every hour by default, giving two benefits:\n"
			+ "(1) better diagnose your network (we provide you with history of your network performance),\n"
			+ "(2) enables our research for long-term network improvement.\n\n"
			+ "We provide the setting to opt out, but we do appreciate you keep this option enabled.\n"
			+ "Thank you for your help!";
	// Options for periodical running
	public static final int PERIODIC_YES = 0;
	public static final int PERIODIC_NO = 1;
	final CharSequence[] periodicItems = { "Yes", "No" };
	final CharSequence[] periodicPrompts = { "Periodic running is enabled",
			"Periodical running is disabled" };
	public static final int NOTIFICATION_YES = 0;
	public static final int NOTIFICATION_NO = 1;
	final CharSequence[] notificationItems = { "Yes", "No" };
	final CharSequence[] notificationPrompts = { "Notification is enabled",
			"Notification is disabled" };

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.preferences);
		// Get the custom preference
		Preference customPref = (Preference) findPreference("PeriodicPref");
		customPref
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						SharedPreferences prefs = PreferenceManager
								.getDefaultSharedPreferences(getBaseContext());
						boolean CheckboxPreference = prefs.getBoolean(
								"PeriodicPref", true);
						if (CheckboxPreference == true) {
							//by default, we run all tests periodically
							enablePeriodicalRun(Preferences.this, "all"); 
							Toast.makeText(getApplicationContext(),
									"enabled periodic running",
									Toast.LENGTH_SHORT).show();
						}
						if (CheckboxPreference == false) {
							disablePeriodicalRun(Preferences.this);
							Toast.makeText(getApplicationContext(),
									"disable periodic running",
									Toast.LENGTH_SHORT).show();
						}

						return true;
					}
				});
		
		Preference intervalPref = (Preference) findPreference("checkinIntervalPref");
		Preference batteryPref = (Preference) findPreference("batteryMinThresPref");
		Preference periodPref = (Preference) findPreference("periodtestPref");
		
		OnPreferenceChangeListener prefChangeListener = new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference,
					Object newValue) {
				String prefKey = preference.getKey();
				if (prefKey
						.compareTo(getString(R.string.checkinIntervalPrefKey)) == 0) {
					try {
						Integer val = Integer.parseInt((String) newValue);
						if (val <= 0 || val > 24) {
							Toast.makeText(
									Preferences.this,
									getString(R.string.invalidCheckinIntervalToast),
									Toast.LENGTH_LONG).show();
							return false;
						}
						return true;
					} catch (ClassCastException e) {
						Logger.e("Cannot cast checkin interval preference value to Integer");
						return false;
					} catch (NumberFormatException e) {
						Logger.e("Cannot cast checkin interval preference value to Integer");
						return false;
					}
				} else if (prefKey.compareTo(getString(R.string.batteryMinThresPrefKey)) == 0) {
					try {
						Integer val = Integer.parseInt((String) newValue);
						if (val < 0 || val > 100) {
							Toast.makeText(Preferences.this,
									getString(R.string.invalidBatteryToast),
									Toast.LENGTH_LONG).show();
							return false;
						}
						return true;
					} catch (ClassCastException e) {
						Logger.e("Cannot cast battery preference value to Integer");
						return false;
					} catch (NumberFormatException e) {
						Logger.e("Cannot cast battery preference value to Integer");
						return false;
					}
				} else if (prefKey.compareTo(getString(R.string.periodtestPrefKey)) == 0) {
					//when new test is selected, disable first and then enalbe with new selected tests.
					disablePeriodicalRun(Preferences.this);
					enablePeriodicalRun(Preferences.this, (String) newValue);
				}
				return true;
			}
		};
		
		intervalPref.setOnPreferenceChangeListener(prefChangeListener);
		batteryPref.setOnPreferenceChangeListener(prefChangeListener);
		periodPref.setOnPreferenceChangeListener(prefChangeListener);
	}

	public static boolean getSharedPreferences(Context ctxt) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(ctxt);
		return true;
	}

	protected Dialog onCreateDialog(int id) {
		Dialog dialog;
		AlertDialog.Builder builder;
		switch (id) {
		case DIALOG_PERIODIC:
			builder = new AlertDialog.Builder(this);
			builder.setTitle("Periodic MobiPerf currently running ...");
			builder.setSingleChoiceItems(periodicItems,
					isPeriodicalRunEnabled(this) ? 0 : 1,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							switch (item) {
							case PERIODIC_YES:
								//enablePeriodicalRun(Preferences.this);
								break;
							case PERIODIC_NO:
								disablePeriodicalRun(Preferences.this);
								break;
							default:
							}
							Toast.makeText(getApplicationContext(),
									periodicPrompts[item], Toast.LENGTH_SHORT)
									.show();
							Preferences.this.dismissDialog(DIALOG_PERIODIC);
						}
					});
			dialog = builder.create();
			break;
		case DIALOG_NOTIFICATION:
			builder = new AlertDialog.Builder(this);
			builder.setTitle("Enable notification");
			builder.setSingleChoiceItems(notificationItems,
					isNotificationEnabled ? 0 : 1,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int item) {
							switch (item) {
							case NOTIFICATION_YES:
								isNotificationEnabled = true;
								if (isPeriodicalRunEnabled(Preferences.this))
								break;
							case NOTIFICATION_NO:
								clearNotification(Preferences.this);
								isNotificationEnabled = false;
								break;
							default:
							}
							Toast.makeText(getApplicationContext(),
									notificationPrompts[item],
									Toast.LENGTH_SHORT).show();
							Preferences.this.dismissDialog(DIALOG_NOTIFICATION);
						}
					});
			dialog = builder.create();
			break;
		case DIALOG_WARNING:
			builder = new AlertDialog.Builder(this);
			builder.setTitle("Notice")
					.setMessage(WARNING)
					.setCancelable(false)
					.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									Preferences.this
											.dismissDialog(DIALOG_WARNING);
								}
							});
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}
		return dialog;
	}

	/**
	 * Functions for periodical running.  Legacy code that might be merged.
	 * 
	 * @author Gary
	 */
	public static final int REQUEST_CODE = 100000;
	public static final long INTERVAL = 3600 * 1000;
	public static final String PERIODIC_FILE = "periodic_file";

	public static boolean isAllowedPeriodicalRun(Context context) {
		String r = Utilities.read_first_line_from_file(PERIODIC_FILE,
				Context.MODE_PRIVATE, context);
		if (r != null && r.equals(PERIODIC_NO + ""))
			return false;
		return true;
	}

	/**
	 * This is called when the "Periodic Running" is checked
	 * @param context
	 * @param periodtest
	 */
	public static void enablePeriodicalRun(Context context, String periodtest) {
		
		Intent intent = new Intent(context, PeriodicTest.class);
    	PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 
    			Config.PERIODIC_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);
    	
    	if(pendingIntent != null){
    	}else{
    		// Create new pending intent
    		Bundle b = new Bundle();
    		b.putString("test", periodtest);
    		intent.putExtras(b);
    		pendingIntent = PendingIntent.getBroadcast(context, Config.PERIODIC_REQUEST_CODE, intent, 0);
    		AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
    		
    		int interval = TimeSetting.getProgress()+10;
    		
    		//If less than 60, this is in minutes
    		if (interval < 60) interval = interval * 1000 * 60;
    		else {
    			interval = interval - 59;
    			interval = interval * 1000 * 3600;
    		}
			
        	alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, 
        			System.currentTimeMillis() + Config.PERIODIC_FIRST_RUN_STARTING_DELAY, 
        			interval, pendingIntent);
        	
    	}
	
	}

	public static void disablePeriodicalRun(Context context) {
		Intent intent = new Intent(context, PeriodicTest.class);
    	PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 
    			Config.PERIODIC_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);
    	if(pendingIntent != null){
    		AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
    		// Cancel alarm
    		alarmManager.cancel(pendingIntent);
    		// Remove the pending intent
    		pendingIntent.cancel();
    	}
	}

	public static boolean isPeriodicalRunEnabled(Context context) {
		Intent intent = new Intent(context, PeriodicTest.class);
    	PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 
    			Config.PERIODIC_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE);
    	if(pendingIntent == null)
    		return false;
    	return true;
	}

	private static void clearNotification(Context context) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(NOTIFICATION_ID);
	}


}

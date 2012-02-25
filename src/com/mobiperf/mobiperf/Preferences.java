/* Copyright 2012 Mobiperf.
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

import com.mobiperf.mobiperf.R;

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
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

//import com.mobiperf.lte.ui.TimeSetting;

public class Preferences extends PreferenceActivity {
	public static final int NOTIFICATION_ID = 0;
	private static boolean isNotificationEnabled = true; // Show notification by
															// default

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
							enablePeriodicalRun(Preferences.this);
							Toast.makeText(getApplicationContext(),
									"enabled the periodic running",
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

		Preference customPref1 = (Preference) findPreference("NotificationPref");

		customPref1
				.setOnPreferenceClickListener(new OnPreferenceClickListener() {
					public boolean onPreferenceClick(Preference preference) {
						SharedPreferences prefs = PreferenceManager
								.getDefaultSharedPreferences(getBaseContext());
						boolean CheckboxPreference = prefs.getBoolean(
								"NotificationPref", true);
						if (CheckboxPreference == true) {
							// test
							// int a = seekbar.getProgress();

							// Log.w("!!!!!!the number is ",
							// String.format("%d",a));

							createNotification(Preferences.this);

							Toast.makeText(getApplicationContext(),
									"enabled the notification",
									Toast.LENGTH_SHORT).show();

						}
						if (CheckboxPreference == false) {
							clearNotification(Preferences.this);
							Toast.makeText(getApplicationContext(),
									"disable the notification",
									Toast.LENGTH_SHORT).show();

						}

						return true;
					}
				});
	}

	public static boolean getSharedPreferences(Context ctxt) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(ctxt);
		return true;
	}

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
								enablePeriodicalRun(Preferences.this);
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
									createNotification(Preferences.this);
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

	/**** Functions for periodical running by Gary ****/

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

	public static void enablePeriodicalRun(Context context) {
		/*
		 * //Log.v("LOG", "registering intent with periodc class"); Intent
		 * intent = new Intent(context, Periodic.class); PendingIntent
		 * pendingIntent = PendingIntent.getBroadcast(context,
		 * Definition.PERIODIC_REQUEST_CODE, intent,
		 * PendingIntent.FLAG_NO_CREATE); //Log.v("LOG",
		 * "PendingIntent.getBroadcast() returns " + pendingIntent);
		 * if(pendingIntent != null){ //debug(context,
		 * "Alarm already registerd"); }else{ //debug(context,
		 * "Register new alarm"); // Create new pending intent pendingIntent =
		 * PendingIntent.getBroadcast(context, Definition.PERIODIC_REQUEST_CODE,
		 * intent, 0); AlarmManager alarmManager = (AlarmManager)
		 * context.getSystemService(ALARM_SERVICE);
		 * 
		 * int interval = TimeSetting.getProgress()+10;
		 * 
		 * //If less than 60, this is in minutes if (interval < 60) interval =
		 * interval * 1000 * 60; else { interval = interval - 59; interval =
		 * interval * 1000 * 3600; } Log.v("4G Test",
		 * "interval is "+interval+" milliseconds");
		 * 
		 * alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
		 * System.currentTimeMillis() +
		 * Definition.PERIODIC_FIRST_RUN_STARTING_DELAY, interval,
		 * pendingIntent); }
		 * 
		 * Utilities.writeToFile(Definition.PERIODIC_FILE, Context.MODE_PRIVATE,
		 * PERIODIC_YES + "", context); if(isNotificationEnabled)
		 * createNotification(context);
		 */
	}

	public static void disablePeriodicalRun(Context context) {
		/*
		 * //Log.v("LOG", "registering intent with periodic class"); Intent
		 * intent = new Intent(context, Periodic.class); PendingIntent
		 * pendingIntent = PendingIntent.getBroadcast(context,
		 * Definition.PERIODIC_REQUEST_CODE, intent,
		 * PendingIntent.FLAG_NO_CREATE); //Log.v("LOG",
		 * "PendingIntent.getBroadcast() returns " + pendingIntent);
		 * if(pendingIntent != null){ //debug("Alarm cancelled"); AlarmManager
		 * alarmManager = (AlarmManager)
		 * context.getSystemService(ALARM_SERVICE); // Cancel alarm
		 * alarmManager.cancel(pendingIntent); // Remove the pending intent
		 * pendingIntent.cancel(); }
		 * 
		 * Utilities.writeToFile(Definition.PERIODIC_FILE, Context.MODE_PRIVATE,
		 * PERIODIC_NO + "", context);
		 * 
		 * clearNotification(context);
		 */
	}

	public static boolean isPeriodicalRunEnabled(Context context) {
		/*
		 * Intent intent = new Intent(context, Periodic.class); PendingIntent
		 * pendingIntent = PendingIntent.getBroadcast(context,
		 * Definition.PERIODIC_REQUEST_CODE, intent,
		 * PendingIntent.FLAG_NO_CREATE); //Log.v("LOG",
		 * "PendingIntent.getBroadcast() returns " + pendingIntent);
		 * if(pendingIntent == null) return false;
		 */
		return true;
	}

	private static void clearNotification(Context context) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(NOTIFICATION_ID);
	}

	// modified by cc-----told by friends that the notification is annoying
	public static void createNotification(Context context) {
		/*
		 * NotificationManager mNotificationManager = (NotificationManager)
		 * context.getSystemService(Context.NOTIFICATION_SERVICE); //2.
		 * Instantiate the Notification: int icon = R.drawable.iconstat;
		 * CharSequence tickerText = "MobiPerf"; long when =
		 * System.currentTimeMillis(); Notification notification = new
		 * Notification(icon, tickerText, when); notification.defaults |=
		 * Notification.FLAG_NO_CLEAR; // Never get cleared notification.flags
		 * |= Notification.FLAG_NO_CLEAR; // Never get cleared // 3. Define the
		 * Notification's expanded message and Intent: CharSequence contentTitle
		 * = "MobiPerf"; CharSequence contentText =
		 * "Periodic test is running ..."; Intent notificationIntent = new
		 * Intent(context, History.class); PendingIntent contentIntent =
		 * PendingIntent.getActivity(context, 0, notificationIntent, 0);
		 * notification.setLatestEventInfo(context, contentTitle, contentText,
		 * contentIntent); // 4. Pass the Notification to the
		 * NotificationManager: mNotificationManager.notify(NOTIFICATION_ID,
		 * notification);
		 */
	}

}

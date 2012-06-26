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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.mobiperf.speedometer.Config;
import com.mobiperf.speedometer.Logger;
import com.mobiperf.speedometer.MeasurementScheduler;
import com.mobiperf.speedometer.UpdateIntent;

/**
 * @author hjx@umich.edu (Junxian Huang) Preference activity allowing user to enable/disable various
 *         settings.
 **/
public class Preferences extends PreferenceActivity {
  public static final int NOTIFICATION_ID = 0;
  private static boolean isNotificationEnabled = true; // Show notification by default

  // Define dialog ids
  protected static final int DIALOG_PERIODIC = 0;
  protected static final int DIALOG_NOTIFICATION = 2;

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
    // Get the custom preference
    addPreferencesFromResource(R.layout.preferences);

    Preference gpsPref = (Preference) findPreference(Config.PREF_KEY_GPS);
    gpsPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean checkboxPreference = prefs.getBoolean(Config.PREF_KEY_GPS, false);
        MeasurementScheduler.setGpsEnabled(checkboxPreference);
        if (checkboxPreference == true) {
          Toast.makeText(getApplicationContext(), R.string.enableGps, Toast.LENGTH_SHORT).show();
        } else {
          Toast.makeText(getApplicationContext(), R.string.disableGps, Toast.LENGTH_SHORT).show();
        }
        return true;
      }
    });

    Preference perodicPref = (Preference) findPreference(Config.PREF_KEY_BACKGROUND);
    perodicPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean checkboxPreference = prefs.getBoolean(Config.PREF_KEY_BACKGROUND, false);
        if (checkboxPreference == true) {
          enableBackground(Preferences.this);
          Toast.makeText(getApplicationContext(), R.string.enableBackground, Toast.LENGTH_SHORT)
              .show();
        } else {
          disableBackground(Preferences.this);
          Toast.makeText(getApplicationContext(), R.string.disableBackground, Toast.LENGTH_SHORT)
              .show();
        }
        return true;
      }
    });

    Preference intervalPref = (Preference) findPreference(Config.PREF_KEY_CHECKIN_INTERVAL);
    Preference batteryPref = (Preference) findPreference(Config.PREF_KEY_BATTERY_THRESHOLD);

    OnPreferenceChangeListener prefChangeListener = new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        String prefKey = preference.getKey();
        if (prefKey.compareTo(Config.PREF_KEY_CHECKIN_INTERVAL) == 0) {
          try {
            Integer val = Integer.parseInt((String) newValue);
            if (val <= 0 || val > 24) {
              Toast.makeText(Preferences.this, getString(R.string.invalidCheckinIntervalToast),
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
        } else if (prefKey.compareTo(Config.PREF_KEY_BATTERY_THRESHOLD) == 0) {
          try {
            Integer val = Integer.parseInt((String) newValue);
            if (val < 0 || val > 100) {
              Toast.makeText(Preferences.this, getString(R.string.invalidBatteryToast),
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
        }
        return true;
      }
    };

    intervalPref.setOnPreferenceChangeListener(prefChangeListener);
    batteryPref.setOnPreferenceChangeListener(prefChangeListener);
  }

  // TODO: useless function to be cleared
  /*
   * public static boolean getSharedPreferences(Context ctxt) { SharedPreferences prefs =
   * PreferenceManager.getDefaultSharedPreferences(ctxt); return true; }
   */

  protected Dialog onCreateDialog(int id) {
    Dialog dialog;
    AlertDialog.Builder builder;
    switch (id) {
    case DIALOG_PERIODIC:
      builder = new AlertDialog.Builder(this);
      builder.setTitle("Periodic MobiPerf currently running ...");
      builder.setSingleChoiceItems(periodicItems, isBackgroundEnabled(this) ? 0 : 1,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              switch (item) {
              case PERIODIC_YES:
                enableBackground(Preferences.this);
                break;
              case PERIODIC_NO:
                disableBackground(Preferences.this);
                break;
              default:
              }
              Toast.makeText(getApplicationContext(), periodicPrompts[item], Toast.LENGTH_SHORT)
                  .show();
              Preferences.this.dismissDialog(DIALOG_PERIODIC);
            }
          });
      dialog = builder.create();
      break;
    case DIALOG_NOTIFICATION:
      builder = new AlertDialog.Builder(this);
      builder.setTitle("Enable notification");
      builder.setSingleChoiceItems(notificationItems, isNotificationEnabled ? 0 : 1,
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
              switch (item) {
              case NOTIFICATION_YES:
                isNotificationEnabled = true;
                if (isBackgroundEnabled(Preferences.this))
                  break;
              case NOTIFICATION_NO:
                clearNotification(Preferences.this);
                isNotificationEnabled = false;
                break;
              default:
              }
              Toast
                  .makeText(getApplicationContext(), notificationPrompts[item], Toast.LENGTH_SHORT)
                  .show();
              Preferences.this.dismissDialog(DIALOG_NOTIFICATION);
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
   * This is called when the "Periodic Running" is checked
   * 
   * @param context
   * @param periodtest
   */
  public static void enableBackground(Context context) {
    MeasurementScheduler.enableAlarm();
  }

  public static void disableBackground(Context context) {
    MeasurementScheduler.cancelAlarm();
  }

  public static boolean isBackgroundEnabled(Context context) {
    return MeasurementScheduler.isBackgroundEnabled();
  }

  private static void clearNotification(Context context) {
    NotificationManager mNotificationManager = (NotificationManager) context
        .getSystemService(Context.NOTIFICATION_SERVICE);
    mNotificationManager.cancel(NOTIFICATION_ID);
  }

  /**
   * As we leave the settings page, changes should be reflected in various applicable components
   * */
  @Override
  protected void onDestroy() {
    super.onDestroy();
    // The scheduler has a receiver monitoring this intent to get the update
    // TODO(Wenjie): Only broadcast update intent when there is real change in the settings
    this.sendBroadcast(new UpdateIntent("", UpdateIntent.PREFERENCE_ACTION));
  }

}
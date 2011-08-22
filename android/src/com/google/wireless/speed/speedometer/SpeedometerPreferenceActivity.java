// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

/**
 * Activity that handles user preferences
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class SpeedometerPreferenceActivity extends PreferenceActivity {
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preference);
    
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    Preference checkinEnabled = findPreference(getString(R.string.checkinEnabledPrefKey));
    Preference intervalPref = findPreference(getString(R.string.checkinIntervalPrefKey));
    Preference batteryPref = findPreference(getString(R.string.batteryMinThresPrefKey));
    
    /* This should never occur. */
    if (checkinEnabled == null || intervalPref == null || batteryPref == null) {
      Log.w(SpeedometerApp.TAG, "Cannot find some of the preferences");
      Toast.makeText(SpeedometerPreferenceActivity.this, 
        getString(R.string.menuInitializationExceptionToast), Toast.LENGTH_LONG).show();
      return;
    }
    
    OnPreferenceChangeListener prefChangeListener = new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        String prefKey = preference.getKey();
        if (prefKey.compareTo(getString(R.string.checkinEnabledPrefKey)) == 0) {
          try {
            Boolean val = (Boolean) newValue;
            Preference intervalPref = findPreference(getString(R.string.checkinIntervalPrefKey));
            intervalPref.setEnabled(val);
          } catch (ClassCastException e) {
            Log.e(SpeedometerApp.TAG, "Cannot cast checkin box preference value to Boolean");
          }
          return true;
        } else if (prefKey.compareTo(getString(R.string.checkinIntervalPrefKey)) == 0) {
          try {
            Integer val = Integer.parseInt((String) newValue);
            if (val <= 0 || val > 24) {
              Toast.makeText(SpeedometerPreferenceActivity.this,
                  getString(R.string.invalidCheckinIntervalToast), Toast.LENGTH_LONG).show();
              return false;
            }
            return true;
          } catch (ClassCastException e) {
            Log.e(SpeedometerApp.TAG, "Cannot cast checkin interval preference value to Integer");
            return false;
          } catch (NumberFormatException e) {
            Log.e(SpeedometerApp.TAG, "Cannot cast checkin interval preference value to Integer");
            return false;
          }
        } else if (prefKey.compareTo(getString(R.string.batteryMinThresPrefKey)) == 0) {
          try {
            Integer val = Integer.parseInt((String) newValue);
            if (val < 0 || val > 100) {
              Toast.makeText(SpeedometerPreferenceActivity.this,
                  getString(R.string.invalidBatteryToast), Toast.LENGTH_LONG).show();
              return false;
            }
            return true;
          } catch (ClassCastException e) {
            Log.e(SpeedometerApp.TAG, "Cannot cast battery preference value to Integer");
            return false;
          } catch (NumberFormatException e) {
            Log.e(SpeedometerApp.TAG, "Cannot cast battery preference value to Integer");
            return false;
          }
        }
        return true;
      }
    };
    
    if (prefs.getBoolean(getString(R.string.checkinEnabledPrefKey),
        Config.DEFAULT_CHECKIN_ENABLED)) {
      Log.i(SpeedometerApp.TAG, "Checkin is enabled in the preference");
      intervalPref.setEnabled(true);
    } else {
      intervalPref.setEnabled(false);
    }

    checkinEnabled.setOnPreferenceChangeListener(prefChangeListener);
    intervalPref.setOnPreferenceChangeListener(prefChangeListener);
    batteryPref.setOnPreferenceChangeListener(prefChangeListener);
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

// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

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
    
    if (prefs.getBoolean(
        getString(R.string.checkinEnabledPrefKey), Config.DEFAULT_CHECKIN_ENABLED)) {
      Log.i(SpeedometerApp.TAG, "Checkin is enabled in the preference");
      intervalPref.setEnabled(true);
    } else {
      intervalPref.setEnabled(false);
    }
    
    /* TODO(Wenjie): Sanitize user inputs */ 
    
    checkinEnabled.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      /** 
       * Enables and disables some preference options as their parent option changes. 
       * */
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
          Boolean val = (Boolean) newValue;
          Preference intervalPref = findPreference(getString(R.string.checkinIntervalPrefKey));
          if (!val.booleanValue()) {
            intervalPref.setEnabled(false);
          } else {
            intervalPref.setEnabled(true);
          }
        } catch (ClassCastException e) {
          Log.e(SpeedometerApp.TAG, "Cannot cast checkin box preference value to Boolean");
        }
        return true;
      }
    });
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

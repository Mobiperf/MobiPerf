// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
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
    
    Preference checkinEnabled = findPreference(getString(R.string.checkinEnabledPrefKey));
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
    this.sendBroadcast(new UpdateIntent("", UpdateIntent.PREFERENCE_ACTION));
  }
}

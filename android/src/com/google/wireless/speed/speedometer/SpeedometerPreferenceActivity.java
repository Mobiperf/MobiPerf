package com.google.wireless.speed.speedometer;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;

public class SpeedometerPreferenceActivity extends PreferenceActivity {
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.preference);
    
    Preference checkinEnabled = findPreference(getString(R.string.checkinEnabledPrefKey));
    checkinEnabled.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
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
}

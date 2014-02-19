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

package com.mobiperf;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.mobiperf.R;

/**
 * Activity that handles user preferences
 */
public class SpeedometerPreferenceActivity extends PreferenceActivity {
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preference);
    
    Preference intervalPref = findPreference(getString(R.string.checkinIntervalPrefKey));
    Preference batteryPref = findPreference(getString(R.string.batteryMinThresPrefKey));
        
    /* This should never occur. */
    if (intervalPref == null || batteryPref == null) {
      Logger.w("Cannot find some of the preferences");
      Toast.makeText(SpeedometerPreferenceActivity.this, 
        getString(R.string.menuInitializationExceptionToast), Toast.LENGTH_LONG).show();
      return;
    }
    
    OnPreferenceChangeListener prefChangeListener = new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        String prefKey = preference.getKey();
        if (prefKey.compareTo(getString(R.string.checkinIntervalPrefKey)) == 0) {
          try {
            Integer val = Integer.parseInt((String) newValue);
            if (val <= 0 || val > 24) {
              Toast.makeText(SpeedometerPreferenceActivity.this,
                  getString(R.string.invalidCheckinIntervalToast), Toast.LENGTH_LONG).show();
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
              Toast.makeText(SpeedometerPreferenceActivity.this,
                  getString(R.string.invalidBatteryToast), Toast.LENGTH_LONG).show();
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
    
    ListPreference lp = (ListPreference)findPreference(Config.PREF_KEY_ACCOUNT);
    final CharSequence[] items = AccountSelector.getAccountList(getApplicationContext());
    lp.setEntries(items);
    lp.setEntryValues(items);
   
    // Restore current settings.
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    String selectedAccount = prefs.getString(Config.PREF_KEY_SELECTED_ACCOUNT, null);
    if (selectedAccount != null) {
      lp.setValue(selectedAccount);
    }
    
    lp.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String account = newValue.toString();
        Logger.i("account selected is: " + account);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Config.PREF_KEY_SELECTED_ACCOUNT, account);
        editor.commit();
        return true;
      }
    });
    
    // Restore current data limit settings
    ListPreference dataLimitLp = (ListPreference)findPreference(Config.PREF_KEY_DATA_LIMIT);
    final CharSequence[] dataLimitItems=new CharSequence[5];
    dataLimitItems[0]="50 MB";
    dataLimitItems[1]="100 MB";
    dataLimitItems[2]="250 MB";
    dataLimitItems[3]="500 MB";
    dataLimitItems[4]="Unlimited";
    dataLimitLp.setEntries(dataLimitItems);
    dataLimitLp.setEntryValues(dataLimitItems);
 
    String selectedDataLimitAccount = prefs.getString(Config.PREF_KEY_SELECTED_DATA_LIMIT, null);
    if (selectedDataLimitAccount != null) {
    	dataLimitLp.setValue(selectedDataLimitAccount);
    }else{
    	dataLimitLp.setValue("250 MB");
    }
    
    /**
     * If the data limit changes, update it immediately
     */
    dataLimitLp.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String limit = newValue.toString();
        Logger.i("new data limit is selected: " + limit);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Config.PREF_KEY_SELECTED_DATA_LIMIT, limit);
        editor.commit();
        return true;
      }
    });
    
    
    intervalPref.setOnPreferenceChangeListener(prefChangeListener);
    batteryPref.setOnPreferenceChangeListener(prefChangeListener);
  }
  
  /** 
   * As we leave the settings page, changes should be reflected in various applicable components
   * */
  @Override
  protected void onDestroy() {
    super.onDestroy();
    // The scheduler has a receiver monitoring this intent to get the update.
    // TODO(Wenjie): Only broadcast update intent when there is real change in the settings.
    this.sendBroadcast(new UpdateIntent("", UpdateIntent.PREFERENCE_ACTION));
  }
}

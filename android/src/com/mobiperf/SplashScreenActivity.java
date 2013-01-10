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

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import com.mobiperf.R;

/**
 * The splash screen for Speedometer
 */
public class SplashScreenActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.splash_screen);
    // Make sure the splash screen is shown in portrait orientation
    this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
    
    TextView version = (TextView)findViewById(R.id.splash_version);
    
    try {
      PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      version.setText(pInfo.versionName);
    } catch (NameNotFoundException e) {
    }
    
    new Handler().postDelayed(new Runnable() {
      @Override
      public void run() {
        Intent intent = new Intent();
        intent.setClassName(SplashScreenActivity.this.getApplicationContext(),
                            SpeedometerApp.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        SplashScreenActivity.this.getApplication().startActivity(intent);
        SplashScreenActivity.this.finish();
      }
    }, Config.SPLASH_SCREEN_DURATION_MSEC);
  }
}

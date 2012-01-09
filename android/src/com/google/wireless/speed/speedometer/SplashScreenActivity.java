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

package com.google.wireless.speed.speedometer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;

/**
 * The splash screen for Speedometer
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class SplashScreenActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.splash_screen);
    // Make sure the splash screen is shown in portrait orientation
    this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
    
    new Handler().postDelayed(new Runnable() {
      @Override
      public void run() {
        Intent intent = new Intent(SpeedometerApp.class.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        SplashScreenActivity.this.getApplication().startActivity(intent);
        SplashScreenActivity.this.finish();
      }
    }, Config.SPLASH_SCREEN_DURATION_MSEC);
  }
}

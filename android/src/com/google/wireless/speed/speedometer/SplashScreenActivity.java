// Copyright 2011 Google Inc. All Rights Reserved.

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

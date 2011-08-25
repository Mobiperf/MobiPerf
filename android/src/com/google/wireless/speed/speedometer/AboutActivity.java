// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;

/**
 * A page that shows information about the Speedoemter application
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class AboutActivity extends Activity {
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.about_page);    
  }
}

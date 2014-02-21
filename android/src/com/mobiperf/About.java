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
package com.mobiperf;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Display a simple description of this application
 */
public class About extends Activity {
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.about);
    TextView version = (TextView)findViewById(R.id.about_version);
    
    try {
      PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
      version.setText("Version: "+pInfo.versionName);
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    }
  }
}

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

import com.google.wireless.speed.speedometer.util.PhoneUtils;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * A page that shows information about the Speedometer application
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class AboutActivity extends Activity {
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.about_page);
    TextView tv = (TextView) findViewById(R.id.aboutVersionText);
    tv.setText(PhoneUtils.getPhoneUtils().getAppVersionName());
  }
}

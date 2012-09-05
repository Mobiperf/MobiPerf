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
package com.mobiperf.mobiperf;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Displays information and gives an option for the user to switch networks for testing.
 */
public class NetworkToggle extends Activity {
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.network);
    TextView textView = (TextView) findViewById(R.id.network_text2);
    textView.setMovementMethod(ScrollingMovementMethod.getInstance());

    Button button = (Button) findViewById(R.id.network_btn);
    button.setOnClickListener(new OnClickListener() {

      // When the button is clicked, call up android test menu
      // @Override
      public void onClick(View v) {
        String url = "tel:*#*#4636#*#*";
        Intent callint = new Intent();
        callint.setAction(Intent.ACTION_DIAL);
        callint.setData(Uri.parse("tel:" + Uri.encode(url)));
        startActivity(callint);
        finish();
      }
    });
  }
}
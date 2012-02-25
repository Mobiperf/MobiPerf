/* Copyright 2012 Mobiperf.
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

import com.mobiperf.mobiperf.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class About extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		TextView textView = (TextView) findViewById(R.id.about2);
		textView.setMovementMethod(ScrollingMovementMethod.getInstance());
	}

	/******************** Menu starts here by cc ********************/
	// Define menu ids
	protected static final int NEW_TEST = Menu.FIRST;
	protected static final int PAST_RECORD = Menu.FIRST + 5;

	// Create menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, NEW_TEST, 0, "New Test");
		// TODO(cc) : new menu
		menu.add(0, PAST_RECORD, 0, "View past record");
		return true;
	}

	// Deal with menu event
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);
		Log.v("menu", "onOptionsItemSelected " + item.getItemId());
		switch (item.getItemId()) {
		/*
		 * case NEW_TEST: Intent i = new Intent(this,
		 * com.mobiperf.lte.Main.class); startActivityForResult(i, 0); break;
		 */
		}
		return true;

	}

	/******************** Menu ends here ********************/

}

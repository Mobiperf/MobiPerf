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
package com.mobiperf.speedometer.speed;

import com.mobiperf.speedometer.measurements.DnsLookupTask;
import com.mobiperf.speedometer.measurements.HttpTask;
import com.mobiperf.speedometer.measurements.PingTask;
import com.mobiperf.speedometer.measurements.TracerouteTask;
import com.mobiperf.speedometer.measurements.DnsLookupTask.DnsLookupDesc;
import com.mobiperf.speedometer.measurements.HttpTask.HttpDesc;
import com.mobiperf.speedometer.measurements.PingTask.PingDesc;
import com.mobiperf.speedometer.measurements.TracerouteTask.TracerouteDesc;
import com.mobiperf.ui.MobiperfActivity;
import com.mobiperf.ui.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TableLayout;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * The UI Activity that allows users to create their own measurements
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class MeasurementCreationActivity extends Activity {
  
  private static final int NUMBER_OF_COMMON_VIEWS = 1;
  public static final String TAB_TAG = "MEASUREMENT_CREATION";
  
  //private SpeedometerApp parent;
  private String measurementTypeUnderEdit;
  private ArrayAdapter<String> spinnerValues;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.measurement_creation_main);
    
    //assert(this.getParent().getClass().getName().compareTo("SpeedometerApp") == 0);
    //this.parent = (SpeedometerApp) this.getParent();
    
    /* Initialize the measurement type spinner */
    Spinner spinner = (Spinner) findViewById(R.id.measurementTypeSpinner);
    spinnerValues = 
        new ArrayAdapter<String>(this.getApplicationContext(), R.layout.spinner_layout);
    for (String name : MeasurementTask.getMeasurementNames()) {
      spinnerValues.add(name);
    }
    spinnerValues.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(spinnerValues);
    spinner.setOnItemSelectedListener(new MeasurementTypeOnItemSelectedListener());
    spinner.requestFocus();
    /* Setup the 'run' button */
    Button runButton = (Button) this.findViewById(R.id.runTaskButton);
    runButton.setOnClickListener(new ButtonOnClickListener());
    
    this.measurementTypeUnderEdit = PingTask.TYPE;
    setupEditTextFocusChangeListener();
  }
  
  private void setupEditTextFocusChangeListener() {
    EditBoxFocusChangeListener textFocusChangeListener = new EditBoxFocusChangeListener();
    EditText text = (EditText) findViewById(R.id.pingTargetText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = (EditText) findViewById(R.id.tracerouteTargetText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = (EditText) findViewById(R.id.httpUrlText);
    text.setOnFocusChangeListener(textFocusChangeListener);
    text = (EditText) findViewById(R.id.dnsLookupText);
    text.setOnFocusChangeListener(textFocusChangeListener);
  }
  
  @Override
  protected void onStart() {
    super.onStart();
    this.populateMeasurementSpecificArea();
  }
  
  private void clearMeasurementSpecificViews(TableLayout table) {
    for (int i = NUMBER_OF_COMMON_VIEWS; i < table.getChildCount(); i++) {
      View v = table.getChildAt(i);
      v.setVisibility(View.GONE);
    }
  }
  
  private void populateMeasurementSpecificArea() {
    TableLayout table = (TableLayout) this.findViewById(R.id.measurementCreationLayout);
    this.clearMeasurementSpecificViews(table);
    if (this.measurementTypeUnderEdit.compareTo(PingTask.TYPE) == 0) {
      this.findViewById(R.id.pingView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(HttpTask.TYPE) == 0) {
      this.findViewById(R.id.httpUrlView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(TracerouteTask.TYPE) == 0) {
      this.findViewById(R.id.tracerouteView).setVisibility(View.VISIBLE);
    } else if (this.measurementTypeUnderEdit.compareTo(DnsLookupTask.TYPE) == 0) {
      this.findViewById(R.id.dnsTargetView).setVisibility(View.VISIBLE);
    }
  }
  
  private void hideKyeboard(EditText textBox) {
    if (textBox != null) {
      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(textBox.getWindowToken(), 0);
    }
  }
  
  private class ButtonOnClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      MeasurementTask newTask = null;
      boolean showLengthWarning = false;
      try {
        if (measurementTypeUnderEdit.equals(PingTask.TYPE)) {
          EditText pingTargetText = (EditText) findViewById(R.id.pingTargetText);
          Map<String, String> params = new HashMap<String, String>();
          params.put("target", pingTargetText.getText().toString());
          PingDesc desc = new PingDesc(null, Calendar.getInstance().getTime(), null,
              Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
              Config.DEFAULT_USER_MEASUREMENT_COUNT, MeasurementTask.USER_PRIORITY, params);
          newTask = new PingTask(desc, MeasurementCreationActivity.this.getApplicationContext());
        } else if (measurementTypeUnderEdit.equals(HttpTask.TYPE)) {
          EditText httpUrlText = (EditText) findViewById(R.id.httpUrlText);
          Map<String, String> params = new HashMap<String, String>();
          params.put("url", httpUrlText.getText().toString());
          params.put("method", "get");
          HttpDesc desc = new HttpDesc(null, Calendar.getInstance().getTime(), null,
              Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC, Config.DEFAULT_USER_MEASUREMENT_COUNT,
              MeasurementTask.USER_PRIORITY, params);
          newTask = new HttpTask(desc, MeasurementCreationActivity.this.getApplicationContext());
        } else if (measurementTypeUnderEdit.equals(TracerouteTask.TYPE)) {
          EditText targetText = (EditText) findViewById(R.id.tracerouteTargetText);
          Map<String, String> params = new HashMap<String, String>();
          params.put("target", targetText.getText().toString());
          TracerouteDesc desc = new TracerouteDesc(null, Calendar.getInstance().getTime(), null,
              Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC, Config.DEFAULT_USER_MEASUREMENT_COUNT,
              MeasurementTask.USER_PRIORITY, params);
          newTask = new TracerouteTask(desc,
              MeasurementCreationActivity.this.getApplicationContext());
          showLengthWarning = true;
        } else if (measurementTypeUnderEdit.equals(DnsLookupTask.TYPE)) {
          EditText dnsTargetText = (EditText) findViewById(R.id.dnsLookupText);
          Map<String, String> params = new HashMap<String, String>();
          params.put("target", dnsTargetText.getText().toString());
          DnsLookupDesc desc = new DnsLookupDesc(null, Calendar.getInstance().getTime(), null,
              Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC, Config.DEFAULT_USER_MEASUREMENT_COUNT,
              MeasurementTask.USER_PRIORITY, params);
          newTask = new DnsLookupTask(desc,
              MeasurementCreationActivity.this.getApplicationContext());
        }
        
        if (newTask != null) {
          MeasurementScheduler scheduler = MobiperfActivity.scheduler;
          if (scheduler != null && scheduler.submitTask(newTask)) {
            // Broadcast an intent with MEASUREMENT_ACTION so that the scheduler will immediately
            // handles the user measurement 
             
            MeasurementCreationActivity.this.sendBroadcast(
                new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION));
            
            //startActivity(new Intent(this, ResultsConsole.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            
            String toastStr = MeasurementCreationActivity.this.getString(
                R.string.userMeasurementSuccessToast);
            if (showLengthWarning) {
              toastStr += newTask.getDescriptor() + " measurements can be long. Please be patient.";
            }
            Toast.makeText(MeasurementCreationActivity.this, toastStr,
                Toast.LENGTH_LONG).show();
            
            if (scheduler.getCurrentTask() != null) {
              showBusySchedulerStatus();
            }
          } else {
            Toast.makeText(MeasurementCreationActivity.this, R.string.userMeasurementFailureToast,
                Toast.LENGTH_LONG).show();
          }
        }
        
      } catch (Exception e) {
    	  Logger.e("Exception when creating user measurements", e);
        Toast.makeText(MeasurementCreationActivity.this, R.string.invalidUserMeasurementInputToast,
            Toast.LENGTH_LONG).show();
      }
    }
    
  }
  
  private void showBusySchedulerStatus() {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    intent.putExtra(UpdateIntent.STATUS_MSG_PAYLOAD, 
        getString(R.string.userMeasurementBusySchedulerToast));
    sendBroadcast(intent);
  }
  
  private class EditBoxFocusChangeListener implements OnFocusChangeListener {

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
      switch (v.getId()) {
        case R.id.pingTargetText:
          /* TODO(Wenjie): Verify user input
           * */
          break;
        case R.id.httpUrlText:
          /* TODO(Wenjie): Verify user input
           * */
          break;
        default:
          break;
      }
      if (!hasFocus) {
        hideKyeboard((EditText) v);
      }
    }
  }
  
  private class MeasurementTypeOnItemSelectedListener implements OnItemSelectedListener {

    /* Handles the ItemSelected event in the MeasurementType spinner. Populate the
     * measurement specific area based on user input
     */
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
      measurementTypeUnderEdit = 
          MeasurementTask.getTypeForMeasurementName(spinnerValues.getItem((int) id));
      if (measurementTypeUnderEdit != null) {
        populateMeasurementSpecificArea();
      }
    }
    
    @Override
    public void onNothingSelected(AdapterView<?> parent) {
      // TODO(Wenjie): at the moment there is nothing we need to do here
    }
  }
  
  
}

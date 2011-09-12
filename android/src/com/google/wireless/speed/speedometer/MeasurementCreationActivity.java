// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.measurements.DnsLookupTask;
import com.google.wireless.speed.speedometer.measurements.DnsLookupTask.DnsLookupDesc;
import com.google.wireless.speed.speedometer.measurements.HttpTask;
import com.google.wireless.speed.speedometer.measurements.HttpTask.HttpDesc;
import com.google.wireless.speed.speedometer.measurements.PingTask;
import com.google.wireless.speed.speedometer.measurements.PingTask.PingDesc;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask.TracerouteDesc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
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
  
  private SpeedometerApp parent;
  private String measurementTypeUnderEdit;
  private ArrayAdapter<String> spinnerValues;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.setContentView(R.layout.measurement_creation_main);
    
    assert(this.getParent().getClass().getName().compareTo("SpeedometerApp") == 0);
    this.parent = (SpeedometerApp) this.getParent();
    
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
          MeasurementScheduler scheduler = parent.getScheduler();
          if (scheduler != null && scheduler.submitTask(newTask)) {
            /* Broadcast an intent with MEASUREMENT_ACTION so that the scheduler will immediately
             * handles the user measurement 
             * */
            MeasurementCreationActivity.this.sendBroadcast(
                new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION));
            SpeedometerApp parent = (SpeedometerApp) getParent();
            TabHost tabHost = parent.getTabHost();
            tabHost.setCurrentTabByTag(ResultsConsoleActivity.TAB_TAG);
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
        Log.e(SpeedometerApp.TAG, "Exception when creating user measurements", e);
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

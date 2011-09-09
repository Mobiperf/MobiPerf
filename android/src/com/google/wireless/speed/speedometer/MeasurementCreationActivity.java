// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.measurements.HttpTask;
import com.google.wireless.speed.speedometer.measurements.HttpTask.HttpDesc;
import com.google.wireless.speed.speedometer.measurements.PingTask;
import com.google.wireless.speed.speedometer.measurements.PingTask.PingDesc;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask.TracerouteDesc;

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * The UI Activity that allows users to create their own measurements
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class MeasurementCreationActivity extends Activity {
  
  private static final int START_TIME_DIALOG_ID = 0;
  private static final int NUMBER_OF_COMMON_VIEWS = 3;
  private static final DateFormat startTimeFormat = new SimpleDateFormat("HH:mm");
  public static final String TAB_TAG = "MEASUREMENT_CREATION";
  
  private SpeedometerApp parent;
  private String measurementTypeUnderEdit;
  private Date startTime = null;
  private StartTimeSetListener startTimeSetListener;
  private EditText startTimeView;
  private TextView countText;
  private int count;
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
    
    /* Initialize the repeat-count seek bar and text */
    this.countText = (TextView) this.findViewById(R.id.countText);
    this.countText.setText(String.valueOf(Config.DEFAULT_USER_MEASUREMENT_COUNT));
    SeekBar countSeekBar = (SeekBar) this.findViewById(R.id.measurementCountSeekBar);
    countSeekBar.setMax(Config.MAX_USER_MEASUREMENT_COUNT);
    countSeekBar.setProgress(Config.DEFAULT_USER_MEASUREMENT_COUNT);
    countSeekBar.setOnSeekBarChangeListener(new CountSeekBarChangeListener());
    
    /* Start time text initialization */
    this.startTimeView = (EditText) this.findViewById(R.id.start_time_text);
    this.startTime = Calendar.getInstance().getTime();
    this.startTimeView.setText(startTimeFormat.format(startTime));
    this.startTimeSetListener = new StartTimeSetListener();
    this.startTimeView.setOnTouchListener(new StartTimeOnTouchListener());    
    
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
    }
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case START_TIME_DIALOG_ID:
        Calendar nowCal = Calendar.getInstance();
        int minute = nowCal.get(Calendar.MINUTE);
        int hour = nowCal.get(Calendar.HOUR);
        // If user has previously set a start time, use that instead
        if (this.startTime != null) {
          minute = startTime.getMinutes();
          hour = startTime.getHours();
        }
        return new TimePickerDialog(this, startTimeSetListener, hour, minute, false);
    }
    return null;
  }
  
  private class StartTimeSetListener implements OnTimeSetListener {
    /** 
     * Sets the start time according to user input
     */
    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
      // The startTime belongs to the Activity class
      startTime.setHours(hourOfDay);
      startTime.setMinutes(minute);
      startTimeView.setText(startTimeFormat.format(startTime));
    }
    
  }
  
  private class ButtonOnClickListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      TextView countText = (TextView) findViewById(R.id.countText);
      MeasurementTask newTask = null;
      boolean showLengthWarning = false;
      try {
        if (measurementTypeUnderEdit.compareTo(PingTask.TYPE) == 0) {
          try {
            EditText pingTargetText = (EditText) findViewById(R.id.pingTargetText);
            long count = Long.parseLong(countText.getText().toString());
            Map<String, String> params = new HashMap<String, String>();
            params.put("target", pingTargetText.getText().toString());
            PingDesc desc = new PingDesc(null, Calendar.getInstance().getTime(), null,
                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC, count, 
                MeasurementTask.USER_PRIORITY, params);
            newTask = new PingTask(desc, MeasurementCreationActivity.this.getApplicationContext());
          } catch (NumberFormatException e) {
            // This should never happen because we control the text
            Log.wtf(SpeedometerApp.TAG, "Number format exception.");
          }
        } else if (measurementTypeUnderEdit.compareTo(HttpTask.TYPE) == 0) {
          try {
            EditText httpUrlText = (EditText) findViewById(R.id.httpUrlText);
            long count = Long.parseLong(countText.getText().toString());
            Map<String, String> params = new HashMap<String, String>();
            params.put("url", httpUrlText.getText().toString());
            params.put("method", "get");
            HttpDesc desc = new HttpDesc(null, Calendar.getInstance().getTime(), null,
                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC, count, 
                MeasurementTask.USER_PRIORITY, params);
            newTask = new HttpTask(desc, MeasurementCreationActivity.this.getApplicationContext());
          } catch (NumberFormatException e) {
            // This should never happen because we control the text
            Log.wtf(SpeedometerApp.TAG, "Number format exception.");
          }        
        } else if (measurementTypeUnderEdit.compareTo(TracerouteTask.TYPE) == 0) {
          try {
            EditText targetText = (EditText) findViewById(R.id.tracerouteTargetText);
            long count = Long.parseLong(countText.getText().toString());
            Map<String, String> params = new HashMap<String, String>();
            params.put("target", targetText.getText().toString());
            TracerouteDesc desc = new TracerouteDesc(null, Calendar.getInstance().getTime(), null,
                Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC, count, 
                MeasurementTask.USER_PRIORITY, params);
            newTask = new TracerouteTask(desc, 
                MeasurementCreationActivity.this.getApplicationContext());
            showLengthWarning = true;
          } catch (NumberFormatException e) {
            // This should never happen because we control the text
            Log.wtf(SpeedometerApp.TAG, "Number format exception.");
          }
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
  
  private class StartTimeOnTouchListener implements OnTouchListener {
    /**
     * Handles the touch event of the start time EditText
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
      showDialog(START_TIME_DIALOG_ID);
      return true;
    }
  }
  
  private class CountSeekBarChangeListener implements OnSeekBarChangeListener {
    /** 
     * Change the countText to tell the user the current count value
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
      if (fromUser) {
        int validProgress = Math.max(progress, 1);
        count = validProgress;
        countText.setText(String.valueOf(count));
        seekBar.setProgress(validProgress);
      }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
      /* TODO(wenjiezeng): Currently does not need to use this event. Simply a place holder for
       * compilation */
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
      /* TODO(wenjiezeng): Currently does not need to use this event. Simply a place holder for
       * compilation */
    }    
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

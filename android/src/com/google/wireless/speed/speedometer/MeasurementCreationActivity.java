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
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.TimePicker;

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
    this.countText.setText(String.valueOf(PingTask.DEFAULT_PING_CNT_PER_TASK));
    SeekBar countSeekBar = (SeekBar) this.findViewById(R.id.measurementCountSeekBar);
    countSeekBar.setProgress(PingTask.DEFAULT_PING_CNT_PER_TASK);
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
      this.findViewById(R.id.httpMethodView).setVisibility(View.VISIBLE);
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
      if (measurementTypeUnderEdit.compareTo(PingTask.TYPE) == 0) {
        try {
          EditText pingTargetText = (EditText) findViewById(R.id.pingTargetText);
          long count = Long.parseLong(countText.getText().toString());
          Map<String, String> params = new HashMap<String, String>();
          params.put("target", pingTargetText.getText().toString());
          PingDesc desc = new PingDesc(null, Calendar.getInstance().getTime(), null,
              PingTask.DEFAULT_PING_INTERVAL, count, MeasurementTask.USER_PRIORITY, params);
          PingTask newTask = new PingTask(desc, MeasurementCreationActivity.this);
          parent.getScheduler().submitTask(newTask);
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
          RadioButton rb = (RadioButton) findViewById(R.id.http_get_radio);
          if (rb.isChecked()) {
            params.put("method", "get");
          } else {
            params.put("method", "head");
          }
          HttpDesc desc = new HttpDesc(null, Calendar.getInstance().getTime(), null,
              0, count, MeasurementTask.USER_PRIORITY, params);
          HttpTask newTask = new HttpTask(desc, MeasurementCreationActivity.this);
          parent.getScheduler().submitTask(newTask);
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
              TracerouteTask.DEFAULT_PING_INTERVAL, count, MeasurementTask.USER_PRIORITY, params);
          TracerouteTask newTask = new TracerouteTask(desc, MeasurementCreationActivity.this);
          parent.getScheduler().submitTask(newTask);
        } catch (NumberFormatException e) {
          // This should never happen because we control the text
          Log.wtf(SpeedometerApp.TAG, "Number format exception.");
        }
      }
    }
    
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
        count = progress;
        countText.setText(String.valueOf(count));
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

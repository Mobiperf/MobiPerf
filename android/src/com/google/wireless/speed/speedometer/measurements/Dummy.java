package com.google.wireless.speed.speedometer.measurements;

import java.util.Date;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.google.wireless.speed.speedometer.MeasurementError;
import com.google.wireless.speed.speedometer.MeasurementResult;
import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.Speedometer;
import com.google.wireless.speed.speedometer.Util;

public class Dummy extends MeasurementTask {
  
  public Dummy(String taskKey, Date startTime, Date endTime, Integer count, Integer interval,
      Integer priority, Map<String, String> params) {
    super(taskKey, startTime, endTime, count, interval, priority, params);
  }

  public static final String MEASUREMENT_TYPE_DUMMY = "Dummy";
  
  public static String getType() {
    return MEASUREMENT_TYPE_DUMMY;
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    return new DummyResult();
  }
  
  class DummyResult implements MeasurementResult {
    private Date startTime, endTime;
    
    DummyResult() {
      this.startTime = new Date();
      this.endTime = new Date();
    }
    
    @Override
    public String type() {
      return MEASUREMENT_TYPE_DUMMY;
    }

    @Override
    public String summary() {
      return "Dummy measurement result";
    }

    @Override
    public JSONObject result() {
      JSONObject result = new JSONObject();
      JSONObject parameters = new JSONObject();
      JSONObject values = new JSONObject();
      try {
        result.put("type", this.type());
        result.put("values", values);
        result.put("parameters", parameters);
        values.put("startTime", Util.formatDate(this.startTime));
        values.put("endTime", Util.formatDate(this.endTime));
        values.put("summary", this.summary());
        values.put("something_extra", "this is something extra");
      } catch (JSONException e) {
        Log.e(Speedometer.TAG, "Unable to encode result: " + e);
      }
      return result;
    }
  }

}

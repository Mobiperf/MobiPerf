package com.google.wireless.speed.speedometer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.google.wireless.speed.speedometer.measurements.DnsLookup;
import com.google.wireless.speed.speedometer.measurements.Dummy;

/**
 * Represents a scheduled measurement task. Subclasses implement functionality
 * for performing the actual measurement.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
@SuppressWarnings("rawtypes")
public abstract class MeasurementTask implements Callable<MeasurementResult> {
  public String type;
  public String taskKey;
  public Date startTime, endTime;
  public Integer count, interval, priority;
  public Map<String, String> params;
  
  /** Perform the actual measurement. */
  public abstract MeasurementResult call() throws MeasurementError;
  
  /** Return the string indicating the measurement type. */
  public static String getType() {
    return "None";
  }
  
  @SuppressWarnings("unused")
  private Date createTime = null;
  
  @SuppressWarnings("rawtypes")
  private static HashMap<String, Class> measurementTypes;
  
  // Static initializer for type -> Measurement map
  // Add new measurement types here to enable them
  static {
    measurementTypes = new HashMap<String, Class>();
    measurementTypes.put(Dummy.getType(), Dummy.class);
    measurementTypes.put(DnsLookup.getType(), DnsLookup.class);
  }
  
  /**
   * Factory for measurement tasks.
   * @param type Type of measurement task to create.
   * @param deadline Deadline by which it must be started.
   * @param params Map<String, String> of parameters.
   * @return A MeasurementTask instance.
   */
  public static MeasurementTask makeMeasurementTask(
      String type, String taskKey, Date startTime, Date endTime, Integer count, 
      Integer interval, Integer priority, Map<String, String> params)
      throws IllegalArgumentException {
    Class mClass = measurementTypes.get(type);
    if (mClass == null) {
      throw new IllegalArgumentException("Unknown measurement type " + type);
    }
    
    Class[] types = { String.class, Date.class, Date.class, Integer.class, Integer.class,
        Integer.class, Map.class };
    Constructor constructor = null;
    try {
      constructor = mClass.getConstructor(types);
    } catch (SecurityException e) {
      Log.w(Speedometer.TAG, e.getMessage());
      throw new IllegalArgumentException(
          "Could not get consntructor for " + type, e);
    } catch (NoSuchMethodException e) {
      Log.w(Speedometer.TAG, e.getMessage());
      throw new IllegalArgumentException(
          "Could not get consntructor for " + type, e);
    }
    Object[] cstParams = { taskKey, startTime, endTime, count, interval, priority, params };
    try {
      return (MeasurementTask) constructor.newInstance(cstParams);
    } catch (InstantiationException e) {
      Log.w(Speedometer.TAG, e.getMessage());
      throw new IllegalArgumentException(e);
    } catch (IllegalAccessException e) {
      Log.w(Speedometer.TAG, e.getMessage());
      throw new IllegalArgumentException(e);
    } catch (InvocationTargetException e) {
      Log.w(Speedometer.TAG, e.getMessage());
      throw new IllegalArgumentException(e);
    }
  }
  
  public MeasurementTask(String taskKey, Date startTime, Date endTime, Integer count,
      Integer interval, Integer priority, Map<String, String> params) {
    this.createTime = new Date();
    this.taskKey = taskKey;
    this.startTime = startTime;
    this.endTime = endTime;
    this.count = count;
    this.interval = interval;
    this.priority = priority;
    this.params = params;
  }
  
  public static MeasurementTask parseJson(JSONObject json) throws IOException {
    String type, taskKey;
    Date startTime, endTime;
    Integer count, interval, priority;
    HashMap<String, String> params = new HashMap<String, String>();
    
    try {
      type = json.getString("type");
    } catch (JSONException e) {
      throw new IOException("No type specified");
    }
    
    try {
      taskKey = json.getString("key");
    } catch (JSONException e) {
      taskKey = null;
    }
    
    try {
     startTime = Util.parseDate(json.getString("start_time"));
    } catch (JSONException e) {
      startTime = null;
    } catch (ParseException e) {
      startTime = null;
    }
    
    try {
      endTime = Util.parseDate(json.getString("end_time"));
    } catch (JSONException e) {
      endTime = null;
    } catch (ParseException e) {
      endTime = null;
    }
    
    try {
      count = new Integer(json.getInt("count"));
    } catch (JSONException e) {
      count = null;
    }
    
    try {
      interval = new Integer(json.getInt("interval_sec"));
    } catch (JSONException e) {
      interval = null;
    }
    
    try {
      priority = new Integer(json.getInt("priority"));
    } catch (JSONException e) {
      priority = null;
    }
    
    try {
      JSONObject jsonParams = json.getJSONObject("parameters");
      @SuppressWarnings("unchecked")
      Iterator<String> itr = jsonParams.keys();
      while (itr.hasNext()) {
        String key = (String)itr.next();
        params.put(key, jsonParams.getString(key));
      }
    } catch (JSONException e) {
      Log.i(Speedometer.TAG, "Could not parse parameters");
    }
    
    return makeMeasurementTask(type, taskKey, startTime, endTime, count, interval,
        priority, params);
  }
  
  public String toString() {
    return "<MeasurementTask> " + getType() + " params:" + params;
  }

}

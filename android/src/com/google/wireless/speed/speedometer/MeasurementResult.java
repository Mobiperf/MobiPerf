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
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;

import android.util.Log;
import android.util.StringBuilderPrinter;

import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;

/**
 * POJO that represents the result of a measurement
 * @author wenjiezeng@google.com (Wenjie Zeng)
 * @see MeasurementDesc
 */
public class MeasurementResult {   

  private String deviceId;
  private DeviceProperty properties;
  private Date timestamp;
  private boolean success;
  private String taskKey;
  private String type;
  private MeasurementDesc parameters;
  private HashMap<String, String> values;
  
  /**
   * @param deviceProperty
   * @param type
   * @param timestamp
   * @param success
   * @param measurementDesc
   */
  public MeasurementResult(String id, DeviceProperty deviceProperty, String type, 
                           Date timeStamp, boolean success, MeasurementDesc measurementDesc) {
    super();
    this.taskKey = measurementDesc.key;
    this.deviceId = id;
    this.type = type;
    this.properties = deviceProperty;
    this.timestamp = timeStamp;
    this.success = success;
    this.parameters = measurementDesc;
    this.parameters.parameters = null;
    this.values = new HashMap<String, String>();
  }
 
  /* Returns the type of this result */ 
  public String getType() {
    return parameters.getType();
  }
  
  /* Add the measurement results of type String into the class */
  public void addResult(String resultType, Object resultVal) {
    this.values.put(resultType, MeasurementJsonConvertor.toJsonString(resultVal));
  }
  
  /* Returns a string representation of the result */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    StringBuilderPrinter printer = new StringBuilderPrinter(builder);
    Formatter format = new Formatter();
    try {
      if (type == PingTask.TYPE) {
        getPingResult(printer, values);
      } else if (type == HttpTask.TYPE) {
        getHttpResult(printer, values);
      } else if (type == DnsLookupTask.TYPE) {
        getDnsResult(printer, values);
      } else if (type == TracerouteTask.TYPE) {
        getTracerouteResult(printer, values);
      }
      return builder.toString();
    } catch (NumberFormatException e) {
      Log.e(SpeedometerApp.TAG, "Exception occurs during constructing result string for user", e);
    } catch (ClassCastException e) {
      Log.e(SpeedometerApp.TAG, "Exception occurs during constructing result string for user", e);
    } catch (Exception e) {
      Log.e(SpeedometerApp.TAG, "Exception occurs during constructing result string for user", e);
    }
    return "Measurement has failed";
  }
  
  private void getPingResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    PingDesc desc = (PingDesc) parameters;
    printer.println("Ping results for target " + desc.target);
    printer.println("\nTimestamp: " + properties.timestamp);
    
    float value = Float.parseFloat(values.get("mean_rtt_ms"));
    printer.println("Mean round trip time (ms): " + String.format("%.1f", value));
    
    value = Float.parseFloat(values.get("min_rtt_ms"));
    printer.println("Min round trip time (ms): " + String.format("%.1f", value));
    
    value = Float.parseFloat(values.get("max_rtt_ms"));
    printer.println("Max round trip time (ms): " + String.format("%.1f", value));
    
    value = Float.parseFloat(values.get("stddev_rtt_ms"));
    printer.println("Standard deviation: " + String.format("%.1f", value));
  }
  
  private void getHttpResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    HttpDesc desc = (HttpDesc) parameters;
    printer.println("HTTP results for URL " + desc.url);
    printer.println("\nTimestamp: " + properties.timestamp);
    
    int headerLen = Integer.parseInt(values.get("headers_len"));
    int bodyLen = Integer.parseInt(values.get("body_len"));
    int time = Integer.parseInt(values.get("time_ms"));
    
    printer.println("Downloaded bytes: " + (headerLen + bodyLen));
    printer.println("Time spent (ms): " + time);
    printer.println("Bandwidth (Kbps): " + (headerLen + bodyLen) * 8 / time);
  }
  
  private void getDnsResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    DnsLookupDesc desc = (DnsLookupDesc) parameters;
    printer.println("DNS lookup results for target " + desc.target);
    printer.println("\nTimestamp: " + properties.timestamp);
    
    int time = Integer.parseInt(values.get("time_ms"));
    printer.println("Lookup time (ms): " + time);
  }
  
  private void getTracerouteResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    TracerouteDesc desc = (TracerouteDesc) parameters;
    printer.println("Traceroute results for target " + desc.target);
    printer.println("\nTimestamp: " + properties.timestamp);
    
    int hops = Integer.parseInt(values.get("num_hops"));
    for (int i = 0; i < hops; i++) {
      int host_cnt = 1;
      String key = "hop_" + i + "_addr_1";
      printer.println("Hop " + (i + 1) + ": " + values.get(key));
      
      key = "hop_" + i + "_rtt_ms";
      StringBuffer timeStrBuf = new StringBuffer(values.get(key));
      // The first and last character of this string are double quotes.
      timeStrBuf.deleteCharAt(timeStrBuf.length() - 1);
      timeStrBuf.deleteCharAt(0);
      
      float time = Float.parseFloat(timeStrBuf.toString());
      printer.println("Delay for hop " + (i + 1) + " (ms): " + String.format("%.1f", time));
    }
  }
}
 
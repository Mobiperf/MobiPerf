// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;

import java.util.Date;
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
    String result = "";
    for (String key : values.keySet()) {
      String val = values.get(key).toString();
      return key + " is " + val + "\n";      
    }
    return result;
  }  
}
 
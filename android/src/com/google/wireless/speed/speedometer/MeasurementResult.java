package com.google.wireless.speed.speedometer;

import org.json.JSONObject;

/**
 * Interface representing result of a measurement.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
public interface MeasurementResult {
  public String type();
  public String summary();
  public JSONObject result();
}

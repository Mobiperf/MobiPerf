package com.google.wireless.speed.speedometer.measurements;

import java.util.Date;
import java.util.Map;

import com.google.wireless.speed.speedometer.MeasurementError;
import com.google.wireless.speed.speedometer.MeasurementResult;
import com.google.wireless.speed.speedometer.MeasurementTask;

public class NDT extends MeasurementTask {
  public static final String MEASUREMENT_TYPE_NDT = "NDT";
  private String server;
  
  public NDT(String taskKey, Date startTime, Date endTime, Integer count, Integer interval,
      Integer priority, Map<String, String> params) {
    super(taskKey, startTime, endTime, count, interval, priority, params);
    this.server = params.get("server");
  }
  
  public static String getType() {
    return MEASUREMENT_TYPE_NDT;
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    throw new MeasurementError("Not yet implemented");
  }

}

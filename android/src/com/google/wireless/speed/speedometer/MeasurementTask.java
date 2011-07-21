// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;


import com.google.wireless.speed.speedometer.measurements.HttpTask;
import com.google.wireless.speed.speedometer.measurements.PingTask;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask;

import java.io.InvalidClassException;
import java.util.HashMap;
import java.util.concurrent.Callable;

/**
 * Represents a scheduled measurement task. Subclasses implement functionality
 * for performing the actual measurement. Comparable interface allow comparison
 * inside the priority queue.
 * 
 * @author mdw@google.com (Matt Welsh)
 * @author wenjiezeng@google.com (Wenjie Zeng)
 */
@SuppressWarnings("rawtypes")
public abstract class MeasurementTask implements Callable<MeasurementResult>, Comparable {
   
  protected MeasurementDesc measurementDesc;
  protected SpeedometerApp parent;
  protected int progress;
  private static HashMap<String, Class> measurementTypes;
  
  // TODO(Wenjie): Static initializer for type -> Measurement map
  // Add new measurement types here to enable them 
  static {    
    measurementTypes = new HashMap<String, Class>();
    measurementTypes.put(PingTask.TYPE, PingTask.class);
    measurementTypes.put(HttpTask.TYPE, HttpTask.class);
    measurementTypes.put(TracerouteTask.TYPE, TracerouteTask.class);
  }
  
  public static Class getTaskClassForMeasurement(String type) {
    return measurementTypes.get(type);
  }
  
  /* This is put here for consistency that all MeasurementTask should
   * have a getDescClassForMeasurement() method. However, the MeasurementDesc is abstract 
   * and cannot be instantiated */
  public static Class getDescClass() throws InvalidClassException {
    throw new InvalidClassException("getDescClass() should only be invoked on "
        + "subclasses of MeasurementTask.");
  }
  
  /**
   * @param measurementDesc
   * @param parent
   */
  protected MeasurementTask(MeasurementDesc measurementDesc, SpeedometerApp parent) {
    super();
    this.measurementDesc = measurementDesc;
    this.parent = parent;
    this.progress = 0;
  }
  
  /* Compare priority as the first order. Then compare start time.*/
  @Override
  public int compareTo(Object t) {
    MeasurementTask another = (MeasurementTask) t;
    Long myPrority = this.measurementDesc.priority;
    Long anotherPriority = another.measurementDesc.priority;
    int priorityComparison = myPrority.compareTo(anotherPriority); 
    if (priorityComparison == 0) {
      return this.measurementDesc.startTime.compareTo(another.measurementDesc.startTime);
    } else {
      return priorityComparison;
    }
  }  
  
  public long timeFromExecution() {
    return this.measurementDesc.startTime.getTime() - System.currentTimeMillis();
  }
  
  public boolean isPassedDeadline() {
    if (this.measurementDesc.endTime == null) { 
      return false;
    } else {
      long endTime = this.measurementDesc.endTime.getTime();
      return endTime <= System.currentTimeMillis();
    }
  }
  
  public String getMeasurementType() {
    return this.measurementDesc.type;
  }
  
  public MeasurementDesc getDescription() {
    return this.measurementDesc;
  }
  
  @Override
  public abstract MeasurementResult call() throws MeasurementError; 
  
  /** Return the string indicating the measurement type. */
  public abstract String getType();
  
  /* Place holder in case user wants to view the progress of active measurements*/
  public int getProgress() {
    return this.progress;
  }
    
  @Override
  public String toString() {
    return this.measurementDesc.toString();
  }
}

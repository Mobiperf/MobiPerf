// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;


import com.google.wireless.speed.speedometer.measurements.DnsLookupTask;
import com.google.wireless.speed.speedometer.measurements.HttpTask;
import com.google.wireless.speed.speedometer.measurements.PingTask;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask;

import android.content.Context;

import java.io.InvalidClassException;
import java.util.HashMap;
import java.util.Set;
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
  // the priority queue we use put the smallest element in the head of the queue
  public static final int USER_PRIORITY = Integer.MIN_VALUE;
  protected MeasurementDesc measurementDesc;
  protected Context parent;
  protected int progress;
  private static HashMap<String, Class> measurementTypes;
  // Maps between the type of task and its readable name
  private static HashMap<String, String> measurementDescToType;
  
  // TODO(Wenjie): Static initializer for type -> Measurement map
  // Add new measurement types here to enable them 
  static {    
    measurementTypes = new HashMap<String, Class>();
    measurementDescToType = new HashMap<String, String>();
    measurementTypes.put(PingTask.TYPE, PingTask.class);
    measurementDescToType.put(PingTask.DESCRIPTOR, PingTask.TYPE);
    measurementTypes.put(HttpTask.TYPE, HttpTask.class);
    measurementDescToType.put(HttpTask.DESCRIPTOR, HttpTask.TYPE);
    measurementTypes.put(TracerouteTask.TYPE, TracerouteTask.class);
    measurementDescToType.put(TracerouteTask.DESCRIPTOR, TracerouteTask.TYPE);
    measurementTypes.put(DnsLookupTask.TYPE, DnsLookupTask.class);
    measurementDescToType.put(DnsLookupTask.DESCRIPTOR, DnsLookupTask.TYPE);
  }
  
  /** Gets the currently available measurement types*/
  public static Set<String> getMeasurementNames() {
    return measurementDescToType.keySet();
  }
  
  /** Get the type of a measurement based on its name. Type is for JSON interface only
   * where as measurement name is a readable string for the UI */
  public static String getTypeForMeasurementName(String name) {
    return measurementDescToType.get(name);
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
  protected MeasurementTask(MeasurementDesc measurementDesc, Context parent) {
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
    if (priorityComparison == 0 && 
        this.measurementDesc.startTime != null && another.measurementDesc.startTime != null) {
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

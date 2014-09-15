/* Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mobiperf;

import android.content.Context;
import android.content.Intent;

import com.mobiperf.measurements.DnsLookupTask;
import com.mobiperf.measurements.HttpTask;
import com.mobiperf.measurements.PingTask;
import com.mobiperf.measurements.TracerouteTask;
import com.mobiperf.measurements.UDPBurstTask;
import com.mobiperf.measurements.TCPThroughputTask;
import com.mobiperf.measurements.RRCTask;

import java.io.InvalidClassException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Represents a scheduled measurement task. Subclasses implement functionality
 * for performing the actual measurement. Comparable interface allow comparison
 * inside the priority queue.
 */
@SuppressWarnings("rawtypes")
public abstract class MeasurementTask implements Callable<MeasurementResult>, Comparable {
  // the priority queue we use puts the smallest element in the head of the queue
  public static final int USER_PRIORITY = Integer.MIN_VALUE;
  public static final int INVALID_PRIORITY = Integer.MAX_VALUE;
  public static final int INFINITE_COUNT = -1;
  
  protected MeasurementDesc measurementDesc;
  protected Context parent;
  /* When updating the 'progress' field, ensure that it's within the range between 0 and
   * Config.MAX_PROGRESS_BAR_VALUE, inclusive. Values outside this range have special meanings and
   * can trigger unexpected results.
   */
  protected int progress;
  private static HashMap<String, Class> measurementTypes;
  // Maps between the type of task and its readable name
  private static HashMap<String, String> measurementDescToType;
  // Maps between the type of task and its visibility to UI
  private static HashMap<String, Boolean> measurementUIVisibility;
  
  // TODO(Wenjie): Static initializer for type -> Measurement map
  // Add new measurement types here to enable them 
  static {    
    measurementTypes = new HashMap<String, Class>();
    measurementDescToType = new HashMap<String, String>();
    measurementUIVisibility = new HashMap<String, Boolean>();
    measurementTypes.put(PingTask.TYPE, PingTask.class);
    measurementDescToType.put(PingTask.DESCRIPTOR, PingTask.TYPE);
    measurementUIVisibility.put(PingTask.DESCRIPTOR, true);
    measurementTypes.put(HttpTask.TYPE, HttpTask.class);
    measurementDescToType.put(HttpTask.DESCRIPTOR, HttpTask.TYPE);
    measurementUIVisibility.put(HttpTask.DESCRIPTOR, true);
    measurementTypes.put(TracerouteTask.TYPE, TracerouteTask.class);
    measurementDescToType.put(TracerouteTask.DESCRIPTOR, TracerouteTask.TYPE);
    measurementUIVisibility.put(TracerouteTask.DESCRIPTOR, true);
    measurementTypes.put(DnsLookupTask.TYPE, DnsLookupTask.class);
    measurementDescToType.put(DnsLookupTask.DESCRIPTOR, DnsLookupTask.TYPE);
    measurementUIVisibility.put(DnsLookupTask.DESCRIPTOR, true);
    measurementTypes.put(TCPThroughputTask.TYPE, TCPThroughputTask.class);
    measurementDescToType.put(TCPThroughputTask.DESCRIPTOR, TCPThroughputTask.TYPE);
    measurementUIVisibility.put(TCPThroughputTask.DESCRIPTOR, true);
    measurementTypes.put(RRCTask.TYPE, RRCTask.class);
    measurementDescToType.put(RRCTask.DESCRIPTOR, RRCTask.TYPE);
    // Currently RRC task is not visible to users
    measurementUIVisibility.put(RRCTask.DESCRIPTOR, false);
    measurementTypes.put(UDPBurstTask.TYPE, UDPBurstTask.class);
    measurementDescToType.put(UDPBurstTask.DESCRIPTOR, UDPBurstTask.TYPE);
    measurementUIVisibility.put(UDPBurstTask.DESCRIPTOR, true);
  }
  
  /** Gets the currently available measurement descriptions*/
  public static Set<String> getMeasurementNames() {
    return measurementDescToType.keySet();
  }
  
  /** Gets the currently available measurement types*/
  public static Set<String> getMeasurementTypes() {
    return measurementTypes.keySet();
  }
  
  /** Get the type of a measurement based on its name. Type is for JSON interface only
   * where as measurement name is a readable string for the UI */
  public static String getTypeForMeasurementName(String name) {
    return measurementDescToType.get(name);
  }
  
  /**
   * Get UI visibility for a measurement task
   */
  public static boolean getVisibilityForMeasurementName(String name) {
  	return (boolean)(measurementUIVisibility.get(name));
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
  
  /**
   * Returns a brief human-readable descriptor of the task.
   */
  public abstract String getDescriptor();
  
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
    String result = "[Measurement " + getDescriptor() + " scheduled to run at " + 
        getDescription().startTime + "]";
    
    return this.measurementDesc.toString();
  }
  
  @Override
  public abstract MeasurementTask clone();
  
  /**
   * Stop the measurement, even when it is running. There is no side effect 
   * if the measurement has not started or is already finished.
   */
  public abstract void stop();
  
  /**
   * All measurement tasks must provide measurements of how much data they have
   * used to be fetched when the task completes.  This allows us to make sure we
   * stay under the data limit.
   * 
   * @return Data consumed, in bytes
   */
  public abstract long getDataConsumed();
  
  public void broadcastProgressForUser(int progress) {
    if (measurementDesc.priority == MeasurementTask.USER_PRIORITY) {
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.PROGRESS_PAYLOAD, progress);
      intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.USER_PRIORITY);
      parent.sendBroadcast(intent);
    }
  }
}

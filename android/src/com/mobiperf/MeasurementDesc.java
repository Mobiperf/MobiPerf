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


import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * MeasurementDesc and all its subclasses are POJO classes that encode a measurement 
 * and enable easy (de)serialization. On the other hand {@link MeasurementTask} contains 
 * runtime specific information for task execution. 
 * @see MeasurementTask 
 */
public abstract class MeasurementDesc {
  
  // General parameters that are shared by all measurements
  public String type;
  public String key;
  public Date startTime;
  public Date endTime;
  public double intervalSec;
  public long count;
  public long priority;
  public Map<String, String> parameters;
    
  /**
   * @param type Type of measurement (ping, dns, traceroute, etc.) 
   * that should execute this measurement task.
   * @param startTime Earliest time that measurements can be taken using this Task descriptor. The
   * current time will be used in place of a null startTime parameter. Measurements with
   * a startTime more than 24 hours from now will NOT be run. 
   * @param endTime Latest time that measurements can be taken using this Task descriptor. Tasks 
   * with an endTime before startTime will be canceled. Corresponding to the 24-hour rule in
   * startTime, tasks with endTime later than 24 hours from now will be assigned a new endTime 
   * that ends 24 hours from now.
   * @param intervalSec Minimum number of seconds to elapse between consecutive measurements taken 
   * with this description.
   * @param count Maximum number of times that a measurement should be taken with this 
   * description. A count of 0 means to continue the measurement indefinitely (until end_time).
   * @param priority Larger values represent higher priorities.
   * @param params Measurement parameters Measurement parameters.
   */
  protected MeasurementDesc(String type, String key, Date startTime, 
                            Date endTime, double intervalSec, long count, long priority, 
                            Map<String, String> params) {
    super();
    this.type = type;
    this.key = key;
    if (startTime == null) {
      updateStartTime();
    } else {
      this.startTime = new Date(startTime.getTime());
    }
    long now = System.currentTimeMillis();
    if (endTime == null || 
        endTime.getTime() - now > Config.TASK_EXPIRATION_MSEC) {
      this.endTime = new Date(now + Config.TASK_EXPIRATION_MSEC);
    } else {
      this.endTime = endTime;
    }
    if (intervalSec <= 0) {
      this.intervalSec = Config.DEFAULT_SYSTEM_MEASUREMENT_INTERVAL_SEC;
    } else {
      this.intervalSec = intervalSec;
    }
    this.count = count;
    this.priority = priority;
    this.parameters = params;
  }
  
  /**
   * We might adjust the interval based on the data consumption profile.
   * 
   * In that case, we need to update the start time accordingly.
   */
  public void updateStartTime() {
    Calendar now = Calendar.getInstance();
    now.add(Calendar.SECOND, (int)intervalSec);
    this.startTime = now.getTime();
    
  }
  
  /** Return the type of the measurement (DNS, Ping, Traceroute, etc.)*/
  public abstract String getType();
  
  /** Subclass override this method to initialize measurement specific parameters*/
  protected abstract void initializeParams(Map<String, String> params);
  
  @Override
  public String toString() {
    return "<MeasurementTask> " + this.type + " deadline:" + endTime +
      " params:" + parameters;
  }  

  /**
   * To determine if a task has changed when receiving a new schedule from
   * the server.
   */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof MeasurementDesc)) return false;

    MeasurementDesc otherDesc = (MeasurementDesc) obj;
    if (!this.type.equals(otherDesc.type)) return false;
    if (!this.key.equals(otherDesc.key)) return false;
    if (this.intervalSec != otherDesc.intervalSec) return false;
    if (this.count != otherDesc.count) return false;
    if (this.priority != otherDesc.priority) return false;
    if (this.parameters == null) {
      return otherDesc.parameters == null;
    }
    if (!this.parameters.equals(otherDesc.parameters)) return false;

    return true;
  }
}

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

/**
 * The system defaults.
 */
public interface Config {
  public static final boolean DEFAULT_START_ON_BOOT = false;
  /** Constants used in various measurement tasks */
  public static final float RESOURCE_UNREACHABLE = Float.MAX_VALUE;
  public static final int PING_COUNT_PER_MEASUREMENT = 10;
  public static final int DEFAULT_DNS_COUNT_PER_MEASUREMENT = 1;
  
  // Default interval in seconds between system measurements of a given measurement type
  public static final double DEFAULT_SYSTEM_MEASUREMENT_INTERVAL_SEC = 60 * 60;
  // Default interval in seconds between user measurements of a given measurement type
  public static final double DEFAULT_USER_MEASUREMENT_INTERVAL_SEC = 5;
  // Default value for the '-i' option in the ping command
  public static final double DEFAULT_INTERVAL_BETWEEN_ICMP_PACKET_SEC = 0.5;
  
  public static final float PING_FILTER_THRES = (float) 1.4;
  public static final int MAX_CONCURRENT_PING = 3;
  // Default # of pings per hop for traceroute
  public static final int DEFAULT_PING_CNT_PER_HOP = 3;
  public static final int HTTP_STATUS_OK = 200;
  public static final int THREAD_POOL_SIZE = 1;
  public static final int MAX_TASK_QUEUE_SIZE = 100;
  public static final long MARGIN_TIME_BEFORE_TASK_SCHEDULE = 500;
  public static final long SCHEDULE_POLLING_INTERVAL = 500;
  public static final String INVALID_IP = "";
  
  /** Constants used in MeasurementScheduler.java */
  // The default checkin interval in seconds
  public static final long DEFAULT_CHECKIN_INTERVAL_SEC = 60 * 60L;
  public static final long MIN_CHECKIN_RETRY_INTERVAL_SEC = 20L;
  public static final long MAX_CHECKIN_RETRY_INTERVAL_SEC = 60L;
  public static final int MAX_CHECKIN_RETRY_COUNT = 3;
  public static final long PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC = 2 * 1000L;
  // default minimum battery percentage to run measurements
  public static final int DEFAULT_BATTERY_THRESH_PRECENT = 60;
  public static final boolean DEFAULT_MEASURE_WHEN_CHARGE = true;
  public static final long MIN_TIME_BETWEEN_MEASUREMENT_ALARM_MSEC = 3 * 1000L;
  
  /** Constants used in BatteryCapPowerManager.java */
  /** The default battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_LEVEL = 0;
  /** The default maximum battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_SCALE = 100;
  /** Tasks expire in seven days. Expired tasks will be removed from the scheduler.
   * In general, schedule updates from the server should take care of this automatically. */
  public static final long TASK_EXPIRATION_MSEC = 7 * 24 * 3600 * 1000;

  
  /** Constants used in MeasurementMonitorActivity.java */
  public static final int MAX_LIST_ITEMS = 128;
  
  public static final int INVALID_PROGRESS = -1;
  public static final int MAX_PROGRESS_BAR_VALUE = 100;
  // A progress greater than MAX_PROGRESS_BAR_VALUE indicates the end of the measurement
  public static final int MEASUREMENT_END_PROGRESS = MAX_PROGRESS_BAR_VALUE + 1;
  public static final int DEFAULT_USER_MEASUREMENT_COUNT = 1;
  
  public static final int MAX_USER_MEASUREMENT_COUNT = 10;
  
  public static final long MIN_CHECKIN_INTERVAL_SEC = 3600;
  
  public static final String PREF_KEY_SYSTEM_CONSOLE = "PREF_KEY_SYSTEM_CONSOLE";
  public static final String PREF_KEY_STATUS_BAR = "PREF_KEY_STATUS_BAR";
  public static final String PREF_KEY_SYSTEM_RESULTS = "PREF_KEY_SYSTEM_RESULTS";
  public static final String PREF_KEY_USER_RESULTS = "PREF_KEY_USER_RESULTS";
  public static final String PREF_KEY_COMPLETED_MEASUREMENTS = "PREF_KEY_COMPLETED_MEASUREMENTS";
  public static final String PREF_KEY_FAILED_MEASUREMENTS = "PREF_KEY_FAILED_MEASUREMENTS";
  public static final String PREF_KEY_CONSENTED = "PREF_KEY_CONSENTED";
  public static final String PREF_KEY_ACCOUNT = "PREF_KEY_ACCOUNT";
  public static final String PREF_KEY_SELECTED_ACCOUNT = "PREF_KEY_SELECTED_ACCOUNT";
  public static final String PREF_KEY_SELECTED_DATA_LIMIT = "PREF_KEY_SELECTED_DATA_LIMIT";
  public static final String PREF_KEY_DATA_LIMIT = "PREF_KEY_DATA_LIMIT";
  
  
  public static final int DEFAULT_DATA_MONITOR_PERIOD_DAY= 1;
  
  
  
  /** Constants for the splash screen */
  public static final long SPLASH_SCREEN_DURATION_MSEC = 1500;
}

// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

/**
 * The system defaults.
 * TODO(Wenjie): These should be initialized by strings.xml for easy deployment customization
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public interface Config {  
  /** Constants used in various measurement tasks */
  public static final float RESOURCE_UNREACHABLE = Float.MAX_VALUE;
  public static final String DEFAULT_PING_HOST = "www.dealsea.com";
  public static final int DEFAULT_PING_COUNT_PER_MEASUREMENT = 10;
  public static final int DEFAULT_DNS_COUNT_PER_MEASUREMENT = 1;
  
  public static final double DEFAULT_MEASUREMENT_INTERVAL_SEC = 10 * 60;
  
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
  public static final long DEFAULT_CHECKIN_INTERVAL_SEC = 3 * 60L;
  public static final long MIN_CHECKIN_RETRY_INTERVAL_SEC = 10L;
  public static final long MAX_CHECKIN_RETRY_INTERVAL_SEC = 60L;
  public static final int MAX_CHECKIN_RETRY_COUNT = 3;
  public static final long PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC = 2 * 1000L;
  // default minimum battery percentage to run measurements
  public static final int DEFAULT_BATTERY_THRESH_PRECENT = 60;
  public static final boolean DEFAULT_CHECKIN_ENABLED = true;
  public static final long MIN_TIME_BETWEEN_MEASUREMENTS_MSEC = 11 * 1000L;
  public static final long DELAYED_SLEEP_FOR_THREAD_SPAWNING_MSEC = 1000L;
  public static final long LOG_ALARM_START_DELAY = 10 * 60 * 1000L;
  public static final long LOG_ALARM_INTERVAL_MSEC = 10 * 60 * 1000L;
  
  /** Constants used in BatteryCapPowerManager.java */
  /** The default battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_LEVEL = 0;
  /** The default maximum battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_SCALE = 100;
  /** Tasks expire in one day. Expired tasks will be removed from the scheduler */
  public static final long TASK_EXPIRATION_MSEC = 24 * 3600 * 1000;
}

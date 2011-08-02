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
  public static final int DEDAULT_CHECKIN_INTERVAL_SEC = 2 * 60;
  public static final long PAUSE_BETWEEN_CHECKIN_CHANGE_SEC = 2L;
  // default minimum battery percentage to run measurements
  public static final int DEFAULT_BATTERY_CAP_PRECENT = 60;
  
  /** Constants used in BatteryCapPowerManager.java */
  /** The default battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_LEVEL = 0;
  /** The default maximum battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_SCALE = 100;
}

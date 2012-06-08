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

package com.mobiperf.speedometer;

/**
 * The system defaults.
 * 
 * @author hjx@umich.edu (Junxian Huang)
 * @author wenjiezeng@google.com (Steve Zeng)
 * 
 */
public interface Config {

  public static final String ALLTASK_TYPE = "all";
  public static final String DEFAULT_TEST_URL = "google.com";

  public static final boolean DEFAULT_START_ON_BOOT = true;
  /** Constants used in various measurement tasks */
  public static final float RESOURCE_UNREACHABLE = Float.MAX_VALUE;
  public static final int PING_COUNT_PER_MEASUREMENT = 10;
  public static final int DEFAULT_DNS_COUNT_PER_MEASUREMENT = 1;

  // Default interval in seconds between system measurements of a given
  // measurement type
  public static final double DEFAULT_SYSTEM_MEASUREMENT_INTERVAL_SEC = 15 * 60;
  // Default interval in seconds between user measurements of a given
  // measurement type
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
  public static final int DEFAULT_BATTERY_THRESH_PRECENT = 80;
  public static final boolean DEFAULT_MEASURE_WHEN_CHARGE = true;
  public static final long MIN_TIME_BETWEEN_MEASUREMENT_ALARM_MSEC = 3 * 1000L;

  /** Constants used in BatteryCapPowerManager.java */
  /** The default battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_LEVEL = 0;
  /** The default maximum battery level if we cannot read it from the system */
  public static final int DEFAULT_BATTERY_SCALE = 100;
  /**
   * Tasks expire in one day. Expired tasks will be removed from the scheduler
   */
  public static final long TASK_EXPIRATION_MSEC = 24 * 3600 * 1000;

  /** Constants used in MeasurementMonitorActivity.java */
  public static final int MAX_LIST_ITEMS = 128;

  public static final int INVALID_PROGRESS = -1;
  public static final int MAX_PROGRESS_BAR_VALUE = 100;
  // A progress greater than MAX_PROGRESS_BAR_VALUE indicates the end of the
  // measurement
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
  public static final String PREF_KEY_SELECTED_ACCOUNT = "PREF_KEY_SELECTED_ACCOUNT";
  public static final String PREF_KEY_USER_CLICKED_AGREE = "PREF_KEY_USER_CLICKED_AGREE";

  // Preference keys for settings tab
  public static final String PREF_KEY_GPS = "PREF_KEY_GPS";
  public static final String PREF_KEY_BACKGROUND = "PREF_KEY_BACKGROUND";
  public static final String PREF_KEY_CHECKIN_INTERVAL = "PREF_KEY_CHECKIN_INTERVAL";
  public static final String PREF_KEY_BATTERY_THRESHOLD = "PREF_KEY_BATTERY_THRESHOLD";

  /** Constants for the splash screen */
  public static final long SPLASH_SCREEN_DURATION_MSEC = 1500;

  // change this to be the server you will connect to, either IP or domain
  // name is fine
  public static final String SERVER_NAME = "falcon.eecs.umich.edu";
  public static boolean DEBUG = false;
  public static boolean TEST = false;

  // DON'T CHANGE BELOW
  // port definitions
  public static final int PORT_WHOAMI = 5000;

  public static final int PORT_DOWNLINK = 5001;
  public static final int PORT_UPLINK = 5002;
  // MLab nodes, the above two ports are already occupied = =!
  public static final int PORT_DOWNLINK_MLAB = 6001;
  public static final int PORT_UPLINK_MLAB = 6002;

  public static final int PORT_CONTROL = 5004;
  public static final int PORT_TCPDUMP_REPORT = 5006;
  public static final int PORT_COMMAND = 5010;

  public static final int PORT_DNS = 53;
  public static final int PORT_BT = 6881;
  public static final int PORT_BT_RAND = 5005;
  public static final int PORT_HTTP = 80;

  public static final int TP_DURATION_IN_MILLI = 16000; // 16 seconds for
  // throughput tests
  public static final int TCP_TIMEOUT_IN_MILLI = 10000; // 5 seconds for
  // timeout
  public static final int UDP_TIMEOUT_IN_MILLI = 10000;

  public static final int IP_HEADER_LENGTH = 20;
  public static final int TCP_HEADER_LENGTH = 32;
  public static final int UDP_HEADER_LENGTH = 8;
  public static final int PREFIX_RECEIVE_BUFFER_LENGTH = 1000;
  public static final int TCPDUMP_RECEIVE_BUFFER_LENGTH = 1000000;

  public static final int THROUGHPUT_UP_SEGMENT_SIZE = 1300;
  public static final int THROUGHPUT_DOWN_SEGMENT_SIZE = 2600;

  public static final int GPS_UPDATE_WAITING_TIME = 10000; // wait for 10
  // seconds for
  // GPS to
  // updated

  public static final String TYPE = "android";
  public static final String RESULT_DELIMITER = "-_hjx-_";

  // periodic related
  public static final int PERIODIC_REQUEST_CODE = 100000;
  public static final long PERIODIC_INTERVAL = 60 * 60 * 1000;
  public static final long PERIODIC_FIRST_RUN_STARTING_DELAY = 5 * 60 * 1000;
  public static final String PERIODIC_FILE = "periodic_file";

  // port scanning
  public static int[] PORTS = new int[] { 21, 22, 25, 53, /* 80, */
  110, 135, 139, 143, 161,
  /* 443, */445, 465, 585, 587, 993, 995, 5060, 6881, 5223, 5228, 8080 };
  public static final String[] PORT_NAMES = new String[] { "FTP", "SSH", "SMTP", "DNS", /* "HTTP", */
  "POP", "RPC", "NETBIOS", "IMAP", "SNMP",
  /* "HTTPS", */"SMB", "SMTP SSL", "Secure IMAP", "Auth SMTP", "IMAP SSL", "POP SSL", "SIP",
      "BITTORRENT", "IOS SPECIAL", "ANDROID SPECIAL", "HTTP PROXY" };

  // report
  public static final int REPORT_MAX_PLAINTEXT_LENGTH = 10000;

  // command
  public static final String COMMAND_TCP_UPLINK = "COMMAND:TCP:UPLINK";
  public static final String COMMAND_TCP_DOWNLINK = "COMMAND:TCP:DOWNLINK";
  public static final String COMMAND_REACH_START = "COMMAND:REACH:START";
  public static final String COMMAND_REACH_STOP = "COMMAND:REACH:STOP";

  public static final String COMMAND_MLAB_INIT_UPLINK = "COMMAND:MLAB:INIT:UPLINK";
  public static final String COMMAND_MLAB_INIT_DOWNLINK = "COMMAND:MLAB:INIT:DOWNLINK";
  public static final String COMMAND_MLAB_END_UPLINK = "COMMAND:MLAB:END:UPLINK";
  public static final String COMMAND_MLAB_END_DOWNLINK = "COMMAND:MLAB:END:DOWNLINK";
}

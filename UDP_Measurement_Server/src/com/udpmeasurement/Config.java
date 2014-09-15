/* Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.
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
package com.udpmeasurement;

import java.sql.Date;
import java.text.SimpleDateFormat;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * Provide the definition of constant and logging function
 * which will probabaly be used in other classes
 */
public class Config {
  public static final int DEFAULT_PORT = 31341;
  // Larger then normal Ethernet MTU, leave enough margin
  public static final int BUFSIZE = 1500;
  /**
   *  Min packet size =  (int type) + (int burstCount) + (int packetNum) +
   *                     (int intervalNum) + (long timestamp) +
   *                     (int packetSize) + (int seq) + (int udpInterval)
   *                  =  36
   */
  public static final int MIN_PACKETSIZE = 36;
  // Leave enough margin for min MTU in the link and IP options
  public static final int MAX_PACKETSIZE = 512;
  public static final int DEFAULT_UDP_PACKET_SIZE = 100;
  
  public static final int MAX_BURSTCOUNT = 100;
  /**
   *  TODO(Hongyi): Interval between packets in millisecond level seems too long
   *  for regular UDP transmission. Microsecond level may be better.
   */
  public static final int MAX_INTERVAL = 1;

  public static final int DEFAULT_TIMEOUT = 1000; // Max one-way delay, in msec
  public static final int GLOBAL_TIMEOUT = 60000; // 'Catch-all' case
  
  public static final int PKT_ERROR = 1;
  public static final int PKT_RESPONSE = 2;
  public static final int PKT_DATA = 3;
  public static final int PKT_REQUEST = 4;

  /**
   * print a log message with the current time and extra information 
   * @param a extra information to be logged
   */
  public static void logmsg(String a) {
    long timenow = System.currentTimeMillis();
    Date date = new Date(timenow);
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
    System.out.println(df.format(date) + " " + a);
  }
}

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

import java.util.ArrayList;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * ClientRecord save the information and status of a UDP burst
 * , both uplink and downlink
 */
public class ClientRecord {
  public int seq;
  public int burstCount;
  public int packetReceived;
  public int packetSize;
  public long lastTimestamp;
  public int udpInterval;
  
  public int packetCount;
  public int outOfOrderCount;
  private int maxPacketNum;
  public ArrayList<Long> offsetedDelayList;  
  
  public Thread timeoutChecker;
  public ClientRecord() {
    maxPacketNum = -1;
    packetCount = 0;
    outOfOrderCount = 0;
    offsetedDelayList = new ArrayList<Long>();
  }

  public void addPacketInfo(int packetNum, long delay, long lastTimestamp) {
    if ( packetNum > maxPacketNum ) {
      maxPacketNum = packetNum;
    }
    else {
      outOfOrderCount++;
    }
    offsetedDelayList.add(delay);
    this.lastTimestamp = lastTimestamp;
    packetCount++;
  }

  /**
   * Get inversion number as the metric of UDP out-of-order count
   * @return the inversion number of the current UDP burst
   */
  public int calculateOutOfOrderNum() {
    return outOfOrderCount;
  }

  /**
   * Calculate jitter as the standard deviation of one-way delays. 
   * Clock sync between client and server is not required since the clock
   * offset will be cancelled out during the calculation process
   * @return the jitter of UDP burst
   */
  public long calculateJitter() {
    int size = offsetedDelayList.size();
    if ( size > 1 ) {
      double offsetedDelay_mean = 0;
      for ( long offsetedDelay : offsetedDelayList ) {
        offsetedDelay_mean += (double)offsetedDelay / size;
      }

      double jitter = 0;
      for ( long offsetedDelay : offsetedDelayList ) {
        jitter += ((double)offsetedDelay - offsetedDelay_mean)
            * ((double)offsetedDelay - offsetedDelay_mean)  / (size - 1);
      }
      jitter = Math.sqrt(jitter);
      
      return (long)jitter;
    }
    else {
      return 0;
    }
  }
}

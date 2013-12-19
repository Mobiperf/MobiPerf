/*
 * Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.mobiperf;

import java.util.concurrent.locks.ReentrantLock;

/**
 * The purpose of this class is to prevent periodic Checkin activity from interfering with ongoing
 * RRC inference measurements.
 */
public class RRCTrafficControl {
  private static ReentrantLock trafficLock;
  private static boolean isInitialized = false;

  // Create the lock if there is no lock
  private synchronized static void initialize() {
    if (!isInitialized) {
      trafficLock = new ReentrantLock();
      isInitialized = true;
    }
  }

  // Acquire the traffic lock to block Checkin activity
  public static synchronized boolean PauseTraffic() {
    initialize();
    if (trafficLock.isLocked()) {
      return false;
    }
    trafficLock.lock();
    return true;
  }

  // Allow Checkin activity by releasing the lock
  public static boolean UnPauseTraffic() {
    initialize();
    if (trafficLock.isHeldByCurrentThread()) {
      trafficLock.unlock();
      return true;
    }
    return false;
  }

  // Check whether RRC measurement is ongoing
  public static boolean checkIfPaused() {
    initialize();
    return trafficLock.isLocked();
  }
}

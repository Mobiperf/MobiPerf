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

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * Entry point of the UDP burst server
 */
public class UDPServer {
  /**
   * Main function
   * Check the port and create the receiver thread  
   * @param args port used by server 
   */
  private static final String VERSION = "2.2.3";
  public static void main(String[] args) {
    UDPReceiver deamon;
    int port = 0;
    
    if (args.length == 1) {
      port = Integer.parseInt(args[0]);
      if ( port < 1 || port > 65535 ) {
        Config.logmsg("Invalid port " + port);
        return;
      }
    }
    else {
      port = Config.DEFAULT_PORT;
    }
    System.out.println("UDP Burst server(Ver " + VERSION + ") runs on port " + port);
    try {
      deamon = new UDPReceiver(port);
      new Thread(deamon).start();
    } catch (MeasurementError e) {
      Config.logmsg("Error when creating receiver thread: " + e.getMessage());
    }
  }

}

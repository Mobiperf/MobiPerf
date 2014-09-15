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

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * The thread sends data to the client according to the downlink request packet
 * Therefore, the downlink burst will not block the processing of other uplink 
 * data packet
 */
public class RequestHandler implements Runnable {
  private DatagramSocket socket;
  private ClientIdentifier clientId;
  private ClientRecord  clientRecord;
  
  /**
   * Constructor
   * @param socket the datagram socket created by the receiver thread
   * @param clientId corresponding client identifier
   * @param clientRecord the downlink request
   */
  public RequestHandler(DatagramSocket socket,
                        ClientIdentifier clientId,
                        ClientRecord  clientRecord) {
    this.socket = socket;
    this.clientId = clientId;
    this.clientRecord = clientRecord;
  }

  /**
   * Send a downlink packet according to ClientRecord
   * @throws MeasurementError send failed
   */
  private void sendPacket(MeasurementPacket packet, int packetNum) 
      throws MeasurementError {
    packet.packetNum = packetNum;
    packet.timestamp = System.currentTimeMillis();
    
    byte[] sendBuffer = packet.getByteArray();
    DatagramPacket sendPacket = new DatagramPacket(
      sendBuffer, sendBuffer.length, clientId.addr, clientId.port); 

    try {
      socket.send(sendPacket);
    } catch (IOException e) {
      throw new MeasurementError(
        "Fail to send UDP packet to " + clientId.toString());
    }

    Config.logmsg("Sent response to " + clientId.toString()
      + " type: PKT_DATA b:" + packet.burstCount + " p:" + packet.packetNum 
      + " timestamp:" + packet.timestamp + " s:" + packet.packetSize
      + " seq:" + packet.seq);
  }
  
  /* (non-Javadoc)
   * @see java.lang.Runnable#run()
   * send n=burstCount downlink packets with the interval of udpInterval
   */
  @Override
  public void run() {
    MeasurementPacket dataPacket = new MeasurementPacket(clientId);
    dataPacket.type = Config.PKT_DATA;
    dataPacket.burstCount = clientRecord.burstCount;
    dataPacket.packetSize = clientRecord.packetSize;
    dataPacket.seq = clientRecord.seq;
    
    for ( int i = 0; i < clientRecord.burstCount; i++ ) {
      try {
        sendPacket(dataPacket, i);
      } catch (MeasurementError e) {
        Config.logmsg("Error processing message: " + e.getMessage());
        break;
      }

      try {
        Thread.sleep(clientRecord.udpInterval);
      } catch (InterruptedException e) {
        Config.logmsg("sleep is interrupted: " + e.getMessage());
      }
    }
  }

}

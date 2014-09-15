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

import java.io.*;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * The main receiver thread for UDP Burst Server. 
 * The thread continually receives the packet from client. If the packet
 * contains uplink data, it records the packet's information and send a
 * response when the uplink is finished. Or if the packet is a downlink
 * request, it generates another handler thread to send downlink burst.
 * Otherwise it replies with a error message
 */
public class UDPReceiver implements Runnable {

  public DatagramSocket socket;
  private DatagramPacket receivedPacket;
  private byte[] receivedBuffer;

  private HashMap<ClientIdentifier, ClientRecord> clientMap;

  public UDPReceiver(int port) throws MeasurementError {
    try {
      socket = new DatagramSocket(port);
    } catch (SocketException e) {
      throw new MeasurementError("Failed opening and binding socket!");
    }

    receivedBuffer = new byte[Config.BUFSIZE];
    receivedPacket = new DatagramPacket(receivedBuffer, receivedBuffer.length);

    clientMap = new HashMap<ClientIdentifier, ClientRecord>();
  }

  /* (non-Javadoc)
   * @see java.lang.Runnable#run()
   * Main receiving iteration
   */
  @Override
  public void run() {
    System.out.println("Receiver thread is running...");

    while ( true ) {
      try {
        // get client's request
        socket.setSoTimeout(Config.GLOBAL_TIMEOUT);
        socket.receive(receivedPacket);
        ClientIdentifier clientId = new ClientIdentifier(
          receivedPacket.getAddress(), receivedPacket.getPort()); 
        Config.logmsg("Received message from " + clientId.toString());

        // processing message
        try {
          MeasurementPacket packet = new MeasurementPacket(
              clientId, receivedPacket.getData());
          processPacket(packet);
        } catch (MeasurementError e) {
          Config.logmsg("Error processing message: " + e.getMessage());
        }

      } catch (IOException e) {
        // Timeout, clean all unfinished record and send response to each client
        try {
          removeOldRecord();
        } catch (MeasurementError e1) {
          Config.logmsg("Error sending response when timeout: " + e.getMessage());
        }
      }

    }
  }

  /**
   * The thread continually receives the packet from client. If the packet
   * contains uplink data, it records the packet's information and send a
   * response when the uplink is finished. Or if the packet is a downlink
   * request, it generates another handler thread to send downlink burst.
   * Otherwise it replies with a error message
   * @param packet received packet
   * @throws MeasurementError
   */
  private void processPacket(final MeasurementPacket packet)
      throws MeasurementError {
    if ( packet.type == Config.PKT_REQUEST ) {
      // Create a new thread to burst udp packets
      Config.logmsg("Receive packet request");      

      ClientRecord clientRecord = new ClientRecord();
      clientRecord.seq = packet.seq;
      clientRecord.burstCount = packet.burstCount;
      clientRecord.packetSize = packet.packetSize;
      clientRecord.udpInterval = packet.udpInterval;     

      // TODO(Hongyi): setup similar check in the client side
      if ( clientRecord.burstCount <= 0 ) {
        throw new MeasurementError("Burst count should be positive, not " +
            clientRecord.burstCount);
      }  
      if ( clientRecord.burstCount > Config.MAX_BURSTCOUNT ) {
        throw new MeasurementError("Burst count should be not bigger than " +
            Config.MAX_BURSTCOUNT + ", not " + clientRecord.burstCount);
      }
      if ( clientRecord.packetSize < Config.MIN_PACKETSIZE ) {
        throw new MeasurementError("Request packet size " +
            clientRecord.packetSize + " shorter than min packet size " +
            Config.MIN_PACKETSIZE);
      }
      if ( clientRecord.packetSize > Config.MAX_PACKETSIZE ) {
        throw new MeasurementError("Request packet size " +
            clientRecord.packetSize + " longer than max packet size " +
            Config.MAX_PACKETSIZE);
      }
      if ( clientRecord.udpInterval < 0 ) {
        throw new MeasurementError("Request interval " +
            clientRecord.udpInterval + " should be not negtive, not " +
            clientRecord.udpInterval);
      }
      if ( clientRecord.udpInterval > Config.MAX_INTERVAL ) {
        throw new MeasurementError("Request interval " +
            clientRecord.udpInterval + " longer than max interval " +
            Config.MAX_INTERVAL);
      }
      
      // Create a new thread for downlink burst. Otherwise the uplink burst
      // at the same time may be blocked and lead to wrong delay estimation 
      RequestHandler respHandle = new RequestHandler(socket,
        packet.clientId, clientRecord);
      new Thread(respHandle).start();      
    }
    else if ( packet.type == Config.PKT_DATA )  { 
      // Look up the client map to find the corresponding recorder
      // , or create a new one. Then record the packet's content
      // After received all the packets in a burst or timeout,
      // send a request back

      ClientRecord clientRecord;
      if ( clientMap.containsKey(packet.clientId) ) {
        clientRecord = clientMap.get(packet.clientId);
        int seq = packet.seq;

        // seq must stay the same for one burst
        if ( seq == clientRecord.seq ) {
          long timeNow = System.currentTimeMillis();
          clientRecord.addPacketInfo(packet.packetNum, 
            timeNow - packet.timestamp, timeNow);
        }
        else {
          Config.logmsg("client sent a different sequence number! old " + 
            clientRecord.seq + " => " + "new " + seq);
          sendPacket(Config.PKT_ERROR, packet.clientId, null);
          clientMap.remove(packet.clientId);
          throw new MeasurementError( packet.clientId.toString() + 
            " send a new seq " + seq + " different from current seq " +
              clientRecord.seq);
        }
      }
      else {    // Receive the first UDP packet from a new client 
        long timeNow = System.currentTimeMillis();
        clientRecord = new ClientRecord();
        clientRecord.burstCount = packet.burstCount;
        clientRecord.packetSize = packet.packetSize;
        clientRecord.seq = packet.seq;
        clientRecord.addPacketInfo(packet.packetNum, 
          timeNow - packet.timestamp, timeNow);
        
        clientRecord.timeoutChecker = new Thread(new Runnable() {
          /*
           * (non-Javadoc)
           * @see java.lang.Runnable#run()
           * Check whether this client is timeout. If so, send result back 
           * and remove it from client map.
           */
          @Override
          public void run() {
            long timeToSleep = Config.DEFAULT_TIMEOUT;
            while ( true ) {
              try {
                Thread.sleep(timeToSleep);
              } catch (InterruptedException e1) {
                Config.logmsg(e1.getMessage());
              }

              ClientRecord clientRecord = clientMap.get(packet.clientId);
              if ( clientRecord == null
                  || clientRecord.packetCount == clientRecord.burstCount ) {
                // UDP burst finished. No need to handle timeout
                return;
              }
              timeToSleep = clientRecord.lastTimestamp + Config.DEFAULT_TIMEOUT
                  - System.currentTimeMillis();
              if ( timeToSleep <= 0 ) {
                Config.logmsg("Client " + packet.clientId.toString() + " timeouted");
                try {
                  sendPacket(Config.PKT_RESPONSE, packet.clientId, clientRecord);
                  return;
                } catch (MeasurementError e) {
                  Config.logmsg(e.getMessage());
                  return;
                } finally {
                  clientMap.remove(packet.clientId);
                }
              }
            }
          }
          
        });
        clientRecord.timeoutChecker.start();

        clientMap.put(packet.clientId, clientRecord);
      }

      Config.logmsg("Receive data packet s:" + clientRecord.seq + " b:" +
          clientRecord.burstCount + " p:" + packet.packetNum);

      if (clientRecord.packetCount == clientRecord.burstCount) {
        try {
          sendPacket(Config.PKT_RESPONSE, packet.clientId, clientRecord);
          return;
        } catch (MeasurementError e) {
          throw e;
        } finally {
          clientMap.remove(packet.clientId);
        }
      }
    }
    else {
      // Not data or request packet, send error packet back
      Config.logmsg("Received malformed packet! Type " + packet.type);
      sendPacket(Config.PKT_ERROR, packet.clientId, null);
    }
  }

  /**
   * Send packet according to the type and clientRecord
   * @param type the type of the packet to be sent
   * @param clientId the corresponding client identifier
   * @param clientRecord the other information needed in creating packet 
   * @throws MeasurementError
   */
  private void sendPacket(int type, ClientIdentifier clientId,
                          ClientRecord clientRecord) throws MeasurementError {
    MeasurementPacket packet = new MeasurementPacket(clientId);
    if ( type == Config.PKT_ERROR ) {
      MeasurementPacket errorPacket = packet;
      errorPacket.type = Config.PKT_ERROR;
      errorPacket.packetSize = Config.MIN_PACKETSIZE;
    }
    else if ( type == Config.PKT_DATA ) {
      MeasurementPacket dataPacket = packet;
      dataPacket.type = Config.PKT_DATA;
      dataPacket.burstCount = clientRecord.burstCount;
      dataPacket.packetNum = clientRecord.packetReceived;
      dataPacket.timestamp = System.currentTimeMillis(); 
      dataPacket.packetSize = clientRecord.packetSize;
      dataPacket.seq = clientRecord.seq;
    }
    else if ( type == Config.PKT_RESPONSE ) {
      MeasurementPacket responsePacket = packet;
      responsePacket.type = Config.PKT_RESPONSE;
      responsePacket.burstCount = clientRecord.burstCount;
      responsePacket.outOfOrderNum = clientRecord.calculateOutOfOrderNum(); 
      // Store jitter in the field timestamp
      responsePacket.timestamp = clientRecord.calculateJitter();
      responsePacket.packetNum = clientRecord.packetCount;
      responsePacket.packetSize = clientRecord.packetSize;
      responsePacket.seq = clientRecord.seq;
    }

    byte[] sendBuffer = packet.getByteArray();
    DatagramPacket sendPacket = new DatagramPacket(
      sendBuffer, sendBuffer.length, clientId.addr, clientId.port); 

    try {
      socket.send(sendPacket);
    } catch (IOException e) {
      throw new MeasurementError(
        "Fail to send UDP packet to " + clientId.toString());
    }

    Config.logmsg("Sent response to " + clientId.toString() + " type:" + type
      + " b:" + packet.burstCount + " p:" + packet.packetNum + " out_of_order:"
      + packet.outOfOrderNum + " j:" + packet.timestamp + " s:" 
      + packet.packetSize);
  }

  private void removeOldRecord() throws MeasurementError {
    for(Map.Entry<ClientIdentifier, ClientRecord> entry : clientMap.entrySet()){
      sendPacket(Config.PKT_RESPONSE, entry.getKey(), entry.getValue());
    }
    clientMap.clear();
  }
}

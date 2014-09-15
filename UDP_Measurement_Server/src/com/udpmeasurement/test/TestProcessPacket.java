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
package com.udpmeasurement.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;
import com.udpmeasurement.Config;
import com.udpmeasurement.MeasurementError;
import com.udpmeasurement.MeasurementPacket;
import com.udpmeasurement.UDPReceiver;


/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * Unit test for packet processing
 */
public class TestProcessPacket {
  private UDPReceiver tmpReceiver;
  private Method processPacket;
  private MeasurementPacket packet;
  
  /**
   * Create the receiver class, the reflection method for processPacket due to
   * its visability and a received packet 
   * @throws NoSuchMethodException
   * @throws SecurityException
   * @throws UnknownHostException
   */
  private void init()
      throws NoSuchMethodException, SecurityException, UnknownHostException {
    tmpReceiver = null;
    try {
      tmpReceiver = new UDPReceiver(3131);
    } catch (MeasurementError e) {
      e.printStackTrace();
    }
    
    processPacket = UDPReceiver.class.getDeclaredMethod("processPacket",
                                        new Class[]{MeasurementPacket.class});
    processPacket.setAccessible(true);
    
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    byte[] rawData = new byte[Config.DEFAULT_UDP_PACKET_SIZE];
    packet = null;
    try {
      packet = new MeasurementPacket(id1, rawData);
    } catch (MeasurementError e) {
      e.printStackTrace();
    }
  }
  
  @Test(expected = MeasurementError.class)
  public void TestProcessPacketRequestShortPacket() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    packet.burstCount = 2;  // 1 <= burstCount <= MAX_BURSTCOUNT
    packet.packetSize = Config.MIN_PACKETSIZE - 1; // short packet!
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }

  @Test(expected = MeasurementError.class)
  public void TestProcessPacketRequestLongPacket() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    packet.burstCount = 2;  // 1 <= burstCount <= MAX_BURSTCOUNT
    packet.packetSize = Config.MAX_PACKETSIZE + 1; // long packet!
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }

  @Test(expected = MeasurementError.class)
  public void TestProcessPacketRequestNegBurst() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    packet.burstCount = -1;  // burstCount < 1!
    // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    packet.packetSize = Config.MAX_PACKETSIZE + 1; 
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }
  

  @Test(expected = MeasurementError.class)
  public void TestProcessPacketRequestHugeBurst() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_REQUEST;
    // burstCount > MAX_BURSTCOUNT!
    packet.burstCount = Config.MAX_BURSTCOUNT + 1;
    // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    packet.packetSize = Config.MAX_PACKETSIZE + 1;
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real cause, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }
  

  @Test(expected = MeasurementError.class)
  public void TestProcessPacketDataSeqChange() 
      throws Throwable {
    init();
    
    packet.type = Config.PKT_DATA;
    packet.burstCount = 16;  // 1 <= burstCount <= MAX_BURSTCOUNT
    // MIN_PACKETSIZE <= packetSize <= MAX_PACKETSIZE
    packet.packetSize = Config.DEFAULT_UDP_PACKET_SIZE;
    packet.packetNum = 0;
    packet.seq = 1024;
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real exception, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
    
    packet.packetNum = 1;
    packet.seq = 2048;  // 2048 != 1024
    
    try {
      processPacket.invoke(tmpReceiver, packet);
    } catch (InvocationTargetException e) {
      // InvocationTargetException wrapped the real exception, just unwrap it
      throw e.getCause();
    } finally {
      tmpReceiver.socket.close();
    }
  }
  
}

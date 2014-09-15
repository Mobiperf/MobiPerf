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

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;
import com.udpmeasurement.Config;
import com.udpmeasurement.MeasurementError;
import com.udpmeasurement.MeasurementPacket;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * Unit test for MeasurementPacket.java 
 */
public class TestMeasurementPacket {
  /**
   * Test whether pack and unpack work as describe
   * @throws UnknownHostException
   */
  @Test
  public void TestPacketPackAndUnpack() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    byte[] rawData = new byte[Config.DEFAULT_UDP_PACKET_SIZE];
    for ( int i = 0; i < rawData.length; i++ ) {
      rawData[i] = 8;
    }
    MeasurementPacket packet = null;
    try {
      packet = new MeasurementPacket(id1, rawData);
    } catch (MeasurementError e) {
      e.printStackTrace();
    }
    byte[] newData = null;
    try {
      newData = packet.getByteArray();
    } catch (MeasurementError e) {
      e.printStackTrace();
    }
    for ( int i = 0; i < newData.length; i++ ) {
      assertEquals("Byte should not change after pack and unpack",
        newData[i], rawData[i]);
    }
  }

}

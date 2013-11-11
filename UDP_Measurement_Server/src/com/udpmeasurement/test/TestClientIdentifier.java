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
import java.util.HashSet;

import org.junit.Test;

import com.udpmeasurement.ClientIdentifier;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * Unit test for clientIndentifier.java, validate equals and hashcode
 * to see whether ClientIdentifier works properly as a key of HashMap
 */
public class TestClientIdentifier {
  /**
   * Test identical objects
   * @throws UnknownHostException
   */
  @Test
  public void TestEqual() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = new ClientIdentifier(addr, port);
    assertEquals("id1 must be identical with id2", id1, id2);
  }
  

  /**
   * Test equals(null)
   * @throws UnknownHostException
   */
  @Test
  public void TestEqualSingleNull() throws UnknownHostException {
    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = null;
    assertFalse("id1 must not be identical with id2", id1.equals(id2));
  }
  
  /**
   * Test whether HashSet can contain a same identifier 
   * @throws UnknownHostException
   */
  @Test
  public void TestHashSet() throws UnknownHostException {
    HashSet<ClientIdentifier> set = new HashSet<ClientIdentifier>();

    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    ClientIdentifier id2 = new ClientIdentifier(addr, port);
    
    set.add(id1);
    assertTrue("id2 should be in the set", set.contains(id2));
  }

  /**
   * Test whether hash collision affects correctness
   * @throws UnknownHostException
   */
  @Test
  public void TestHashSetCollision() throws UnknownHostException {
    HashSet<ClientIdentifier> set = new HashSet<ClientIdentifier>();

    InetAddress addr = InetAddress.getByName("192.168.1.1");
    int port = 1234;
    ClientIdentifier id1 = new ClientIdentifier(addr, port);
    InetAddress addr2 = InetAddress.getByName("192.168.1.2");
    int port2 = 1233;
    ClientIdentifier id2 = new ClientIdentifier(addr2, port2);
    
    set.add(id1);
    assertFalse("Though id2.hashcode == id1.hashcode, id2 should not be in the set",
      set.contains(id2));
  }
}

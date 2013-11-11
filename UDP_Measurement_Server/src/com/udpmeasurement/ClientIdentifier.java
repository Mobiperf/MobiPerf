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

import java.net.InetAddress;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * ClientIdentifier Encapsulate the IP address and the port. It is used as 
 * the key of clientMap to locate corresponding ClientRecord
 */
public class ClientIdentifier {
  InetAddress addr;
  int port;

  public ClientIdentifier (InetAddress addr, int port) {
    this.addr = addr;
    this.port = port;
  }

  @Override
  public String toString() {
    return addr.toString() + "(" + port + ")";
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   * Override equals to ensure its proper behavior as the key
   * of a hash map
   */
  @Override
  public boolean equals(Object another) {
    // null protection
    if ( another == null ) {
      return false;
    }
    if ( another instanceof ClientIdentifier ) {
      ClientIdentifier anotherId = (ClientIdentifier)another;
      if ( this.addr.equals(anotherId.addr) && this.port == anotherId.port ) {
        return true;
      }
      else {
        return false;
      }
    }
    else {
      // not a ClientIdentifier
      return false;
    }
  }
  
  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   * Override hashcode to ensure its proper behavior as the key
   * of a hash map
   */
  @Override
  public int hashCode() {
    // pack the address
    byte[] rawByte = addr.getAddress();
    int rawAddr = 0;
    for ( int i = 0; i < rawByte.length; i++ ) {
      rawAddr <<= 8;
      rawAddr |= ((int)rawByte[i] & 0xff);  // convert to unsigned number
    }
    rawAddr += port;
    
    return rawAddr;
  }
}

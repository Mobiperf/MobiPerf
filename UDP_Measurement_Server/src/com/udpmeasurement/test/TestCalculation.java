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

import org.junit.Test;

import com.udpmeasurement.ClientRecord;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 * Unit Test for calculation in ClientRecord.java
 */
public class TestCalculation {
  /**
   * Inversion pair of <2,3,8,6,1> are <2,1> <3,1> <8,6> <8,1> <6,1>.
   * Inversion number is 5
   */
  @Test
  public void testNormalInversion() {

    ClientRecord cliRec = new ClientRecord();
    cliRec.receivedNumberList.add(2);
    cliRec.receivedNumberList.add(3);
    cliRec.receivedNumberList.add(8);
    cliRec.receivedNumberList.add(6);
    cliRec.receivedNumberList.add(1);
    int result = cliRec.calculateInversionNumber();
    assertEquals ( "Inversion pair of <2,3,8,6,1> should be 5, not " + result,
        5, result );
  }

  /**
   * Inversion pair of <1> are null. Inversion number is 0
   */
  @Test
  public void testInversionSingleInput() {
    ClientRecord cliRec = new ClientRecord();
    cliRec.receivedNumberList.add(1);
    int result = cliRec.calculateInversionNumber();
    assertEquals( "Inversion number of <1> should be 0, not " + result,
        0, result);
  }
  
  /**
   * Jitter(Standard Deviation) of <1, -4, 8, 10, -8> should be 7.66 = 7
   * Since the calculation is made in double, we do not need to consider overflow test
   */
  @Test
  public void testNormalJitter() {
    ClientRecord cliRec = new ClientRecord();
    cliRec.offsetedDelayList.add(1L);
    cliRec.offsetedDelayList.add(-4L);
    cliRec.offsetedDelayList.add(8L);
    cliRec.offsetedDelayList.add(10L);
    cliRec.offsetedDelayList.add(-8L);
    long result = cliRec.calculateJitter();
    assertEquals( "Jitter(Standard Deviation) of <1, -4, 8, 10, -8> should be 7.66 = 7, not " + result,
        7, result);
  }

  /**
   * Jitter(Standard Deviation) of <1> should be 0
   */
  @Test
  public void testJitterSingleValue() {
    ClientRecord cliRec = new ClientRecord();
    cliRec.offsetedDelayList.add(1L);
    long result = cliRec.calculateJitter();
    assertEquals( "Jitter(Standard Deviation) of <1> should be 0, not " + result,
        0L, result);
  }
}

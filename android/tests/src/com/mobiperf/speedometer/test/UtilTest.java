/* Copyright 2012 Google Inc.
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
package com.mobiperf.speedometer.test;

import com.mobiperf.measurements.PingTask.PingDesc;
import com.mobiperf.speedometer.MeasurementTask;
import com.mobiperf.speedometer.SpeedometerApp;
import com.mobiperf.util.MeasurementJsonConvertor;

import android.test.AndroidTestCase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unit test for the Util class
 */
public class UtilTest extends AndroidTestCase {
  public UtilTest() {
    super();
  }
  
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }
  
  @SuppressWarnings("cast")
  public void testGson() {
    String pingExe = "/system/bin/ping";
    String pingServer = "www.dealsea.com";
    Date startTime = Calendar.getInstance().getTime();

    HashMap<String, String> params = new HashMap<String, String>();
    params.put("ping_exe", pingExe);
    params.put("target", pingServer);
    PingDesc pingDesc = new PingDesc(null, startTime, null, 0, 0, 0, params);
    
    try {
      JSONObject jsonObj = MeasurementJsonConvertor.encodeToJson(pingDesc);
      assertTrue(jsonObj.getString("start_time").endsWith("Z"));
      MeasurementTask task = 
          MeasurementJsonConvertor.makeMeasurementTaskFromJson(jsonObj, new SpeedometerApp());
      PingDesc copy = (PingDesc) task.getDescription();
      assertEquals(copy.target, pingDesc.target);
      assertEquals(copy.pingExe, pingDesc.pingExe);
      assertEquals(copy.startTime, pingDesc.startTime);
    } catch (JSONException e1) {
      assertTrue("JSON encodign fails", false);      
    }
  }
  
  public void testPingOutputPatternMatching() {
    String patternStr = "icmp_seq=([0-9]+)\\s.* time=([0-9]+(\\.[0-9]+)?)";
    Pattern pattern = Pattern.compile(patternStr);
    Matcher matcher = pattern.matcher("64 bytes from pz-in-f105.1e100.net (74.125.127.105): " + 
        "icmp_seq=10 ttl=51 time=104 ms");
   
    assertEquals(matcher.find(), true);
    assertEquals(matcher.group(1), "10");
    assertEquals(matcher.group(2), "104");
    
    matcher.reset("64 bytes from pz-in-f105.1e100.net (74.125.127.105): " + 
        "icmp_seq=10 ttl=51 time=104.34 ms");
    
    assertEquals(matcher.find(), true);
    assertEquals(matcher.group(1), "10");
    assertEquals(matcher.group(2), "104.34");
    
    pattern = Pattern.compile("([0-9]+)\\spackets.*\\s([0-9]+)\\sreceived");
    matcher = pattern.matcher("16 packets transmitted, 12 received, 0% packet loss, time 9011ms");
    
    assertEquals(matcher.find(), true);
    assertEquals(matcher.group(1), "16");
    assertEquals(matcher.group(2), "12");
  }
}

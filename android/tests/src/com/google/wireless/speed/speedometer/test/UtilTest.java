// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.test;

import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.SpeedometerApp;
import com.google.wireless.speed.speedometer.measurements.PingTask.PingDesc;
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;

import android.test.AndroidTestCase;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

/**
 * Unit test for the Util class
 * @author wenjiezeng@google.com (Steve Zeng)
 *
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
    String pingServer = "www.dealsea.com";
    Date startTime = Calendar.getInstance().getTime();

    HashMap<String, String> params = new HashMap<String, String>();
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
  
  public void testTimeStamp() {
    Log.i(SpeedometerApp.TAG, 
        MeasurementJsonConvertor.formatDate(Calendar.getInstance().getTime()));
  }
}

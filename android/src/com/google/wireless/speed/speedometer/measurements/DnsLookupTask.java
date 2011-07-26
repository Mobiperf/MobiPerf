// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.measurements;

import com.google.wireless.speed.speedometer.MeasurementDesc;
import com.google.wireless.speed.speedometer.MeasurementError;
import com.google.wireless.speed.speedometer.MeasurementResult;
import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.SpeedometerApp;
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;
import com.google.wireless.speed.speedometer.util.RuntimeUtil;

import android.content.Context;
import android.util.Log;

import java.io.InvalidClassException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * Measures the DNS lookup time
 * 
 * @author mdw@gogole.com (Matt Welsh)
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class DnsLookupTask extends MeasurementTask {
  
  public static final String TYPE = "dns_lookup";
  public static final String DEFAULT_TARGET = "www.google.com";

  /**
   * The description of DNS lookup measurement 
   */
  public static class DnsLookupDesc extends MeasurementDesc {
    private String target;
    private String server;
    
    public DnsLookupDesc(String key, Date startTime, Date endTime,
        double intervalSec, long count, long priority, Map<String, String> params) {
      super(DnsLookupTask.TYPE, key, startTime, endTime, intervalSec, count,
          priority, params);
      initalizeParams(params);
    }

    /* 
     * @see com.google.wireless.speed.speedometer.MeasurementDesc#getType()
     */
    @Override
    public String getType() {
      return DnsLookupTask.TYPE;
    }

    @Override
    protected void initalizeParams(Map<String, String> params) {
      if (this.count == 0) {
        this.count = PingTask.DEFAULT_PING_CNT_PER_TASK;
      }
      
      if ((this.target = params.get("target")) == null) {
        this.target = DnsLookupTask.DEFAULT_TARGET;
      }
      
      this.server = params.get("server");
    }
    
  }
  
  public DnsLookupTask(MeasurementDesc desc, Context parent) {
    super(new DnsLookupDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
      desc.count, desc.priority, desc.parameters), parent);;
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    long t1, t2;
    long totalTime = 0;
    InetAddress resultInet = null;
    int successCnt = 0;
    for (int i = 0; i < this.measurementDesc.count; i++) {
      try {
        DnsLookupDesc taskDesc = (DnsLookupDesc) this.measurementDesc;
        Log.i(SpeedometerApp.TAG, "Running DNS Lookup for target " + taskDesc.target);
        Date startTime = new Date();
        t1 = System.currentTimeMillis();
        InetAddress inet = InetAddress.getByName(taskDesc.target);
        t2 = System.currentTimeMillis();
        if (inet != null) {
          totalTime += (t2 - t1);
          resultInet = inet;
          successCnt++;
        }
      } catch (UnknownHostException e) {
        throw new MeasurementError("Cannot resovle domain name");
      }
    }
    
    if (resultInet != null) {
      Log.i(SpeedometerApp.TAG, "Successfully resolved target address");
      MeasurementResult result = new MeasurementResult(RuntimeUtil.getDeviceInfo().deviceId, 
        RuntimeUtil.getDeviceProperty(), DnsLookupTask.TYPE, Calendar.getInstance().getTime(), 
        true, this.measurementDesc);
      result.addResult("address", resultInet.getHostAddress());
      result.addResult("real_hostname", resultInet.getCanonicalHostName());
      result.addResult("time_ms", totalTime / successCnt);
      Log.i(SpeedometerApp.TAG, MeasurementJsonConvertor.toJsonString(result));
      return result;   
    } else {
      throw new MeasurementError("Cannot resovle domain name");
    }
  }

  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return DnsLookupDesc.class;
  }
  
  @Override
  public String getType() {
    return DnsLookupTask.TYPE;
  }

}

// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.measurements;

import com.google.wireless.speed.speedometer.Config;
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
import java.security.InvalidParameterException;
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
  // Type name for internal use
  public static final String TYPE = "dns_lookup";
  // Human readable name for the task
  public static final String DESCRIPTOR = "DNS Lookup";
  
  private static final String DEFAULT_TARGET = "www.google.com";
  private static int DEFAULT_DNS_CNT_PER_TASK = 10;

  /**
   * The description of DNS lookup measurement 
   */
  public static class DnsLookupDesc extends MeasurementDesc {
    public String target;
    private String server;
    
    public DnsLookupDesc(String key, Date startTime, Date endTime,
        double intervalSec, long count, long priority, Map<String, String> params) {
      super(DnsLookupTask.TYPE, key, startTime, endTime, intervalSec, count,
          priority, params);
      initalizeParams(params);
      if (this.target == null || this.target.length() == 0) {
        throw new InvalidParameterException("LookupDnsTask cannot be created due " +
            " to null target string");
      }
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
      if (params == null) {
        return;
      }
      
      if (this.count == 0) {
        this.count = DEFAULT_DNS_CNT_PER_TASK;
      }
      
      this.target = params.get("target");      
      this.server = params.get("server");
    }
    
  }
  
  public DnsLookupTask(MeasurementDesc desc, Context parent) {
    super(new DnsLookupDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
      desc.count, desc.priority, desc.parameters), parent);
  }
  
  /**
   * Returns a copy of the DnsLookupTask
   */
  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    DnsLookupDesc newDesc = new DnsLookupDesc(desc.key, desc.startTime, desc.endTime, 
      desc.intervalSec, desc.count, desc.priority, desc.parameters);
    return new DnsLookupTask(newDesc, parent);
  }

  @Override
  public MeasurementResult call() throws MeasurementError {   
    long t1, t2;
    long totalTime = 0;
    InetAddress resultInet = null;
    int successCnt = 0;
    for (int i = 0; i < Config.DEFAULT_DNS_COUNT_PER_MEASUREMENT; i++) {
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
        this.progress = 100 * i / Config.DEFAULT_DNS_COUNT_PER_MEASUREMENT;
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
  
  @Override
  public String getDescriptor() {
    return DESCRIPTOR;
  }
}

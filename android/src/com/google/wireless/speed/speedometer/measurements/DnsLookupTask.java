// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.measurements;

import com.google.wireless.speed.speedometer.MeasurementDesc;
import com.google.wireless.speed.speedometer.MeasurementError;
import com.google.wireless.speed.speedometer.MeasurementResult;
import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.SpeedometerApp;

import java.io.InvalidClassException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
    
    protected DnsLookupDesc(String key, Date startTime, Date endTime,
        double intervalSec, long count, long priority, Map<String, String> params) {
      super(DnsLookupTask.TYPE, key, startTime, endTime, intervalSec, count,
          priority, params);
      
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
      if ((this.target = params.get("target")) == null) {
        this.target = DnsLookupTask.DEFAULT_TARGET;
      }
      
      this.server = params.get("server");
    }
    
  }
  
  public DnsLookupTask(MeasurementDesc desc, SpeedometerApp parent) {
    super(desc, parent);
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    try {
      DnsLookupDesc taskDesc = (DnsLookupDesc) this.measurementDesc;
      Date startTime = new Date();
      long t1 = System.currentTimeMillis();
      InetAddress inet = InetAddress.getByName(taskDesc.target);
      long t2 = System.currentTimeMillis();
      
      if (inet != null) {
        /*
        MeasurementResult result = new MeasurementResult(RuntimeUtil.getDeviceInfo().deviceId, 
          RuntimeUtil.getDeviceProperty(), DnsLookupTask.TYPE, Calendar.getInstance().getTime(), 
          true, this.measurementDesc);
        result.addResult("address", inet.getHostAddress());
        result.addResult("real_hostname", inet.getCanonicalHostName());
        result.addResult("time_ms", t2 - t1);
        return result;*/    
        return null;
      } else {
        throw new MeasurementError("Cannot resovle domain name");
      }
      
    } catch (UnknownHostException e) {
      throw new MeasurementError(e.getMessage());
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

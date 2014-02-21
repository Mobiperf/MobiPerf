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
package com.mobiperf.measurements;

import com.mobiperf.Config;
import com.mobiperf.Logger;
import com.mobiperf.MeasurementDesc;
import com.mobiperf.MeasurementError;
import com.mobiperf.MeasurementResult;
import com.mobiperf.MeasurementTask;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;

import android.content.Context;

import java.io.InvalidClassException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.Map;

/**
 * Measures the DNS lookup time
 */
public class DnsLookupTask extends MeasurementTask {
  // Type name for internal use
  public static final String TYPE = "dns_lookup";
  // Human readable name for the task
  public static final String DESCRIPTOR = "DNS lookup";
  
  // Since it's very hard to calculate the data consumed by this task
  // directly, we use a fixed value.  This is on the high side.
  public static final int AVG_DATA_USAGE_BYTE=2000;

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
      initializeParams(params);
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
    protected void initializeParams(Map<String, String> params) {
      if (params == null) {
        return;
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
        Logger.i("Running DNS Lookup for target " + taskDesc.target);
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
      Logger.i("Successfully resolved target address");
      PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
      MeasurementResult result = new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
          phoneUtils.getDeviceProperty(), DnsLookupTask.TYPE, System.currentTimeMillis() * 1000,
          true, this.measurementDesc);
      result.addResult("address", resultInet.getHostAddress());
      result.addResult("real_hostname", resultInet.getCanonicalHostName());
      result.addResult("time_ms", totalTime / successCnt);
      Logger.i(MeasurementJsonConvertor.toJsonString(result));
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
  
  @Override
  public String toString() {
    DnsLookupDesc desc = (DnsLookupDesc) measurementDesc;
    return "[DNS Lookup]\n  Target: " + desc.target + "\n  Interval (sec): " + desc.intervalSec 
        + "\n  Next run: " + desc.startTime;
  }
  
  @Override
  public void stop() {
    //There is nothing we need to do to stop the DNS measurement
  }

  /**
   * Since it is hard to get the amount of data sent directly,
   * use a fixed value.  The data consumed is usually small, and the fixed
   * value is a conservative estimate.
   * 
   * TODO find a better way to get this value
   */
  @Override
  public long getDataConsumed() {
    return AVG_DATA_USAGE_BYTE;
  }
}

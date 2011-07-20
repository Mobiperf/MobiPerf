package com.google.wireless.speed.speedometer.measurements;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.google.wireless.speed.speedometer.MeasurementError;
import com.google.wireless.speed.speedometer.MeasurementResult;
import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.Speedometer;
import com.google.wireless.speed.speedometer.Util;

public class DnsLookup extends MeasurementTask {
  public static final String MEASUREMENT_TYPE_DNSLOOKUP = "DnsLookup";
  private String hostname;
  
  public DnsLookup(String taskKey, Date startTime, Date endTime, Integer count, Integer interval,
      Integer priority, Map<String, String> params) {
    super(taskKey, startTime, endTime, count, interval, priority, params);
    this.hostname = params.get("target");
  }
  
  public static String getType() {
    return MEASUREMENT_TYPE_DNSLOOKUP;
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    try {
      Date startTime = new Date();
      long t1 = System.currentTimeMillis();
      InetAddress inet = InetAddress.getByName(this.hostname);
      long t2 = System.currentTimeMillis();
      Date endTime = new Date();
      return new DnsLookupResult(this.hostname,
          inet.getHostAddress(),
          inet.getHostName(),
          startTime, endTime,
          t2-t1);
    } catch (UnknownHostException e) {
      throw new MeasurementError(e.getMessage());
    }
  }
  
  class DnsLookupResult implements MeasurementResult {
    private String hostname;
    private String address;
    private String realHostname;
    private Date startTime;
    private Date endTime;
    private long timeMs;
    
    public DnsLookupResult(String hostname, String address,
        String realHostname, Date startTime, Date endTime, long timeMs) {
      this.hostname = hostname;
      this.address = address;
      this.realHostname = realHostname;
      this.startTime = startTime;
      this.endTime = endTime;
      this.timeMs = timeMs;
    }
    
    @Override
    public String type() {
      return MEASUREMENT_TYPE_DNSLOOKUP;
    }

    @Override
    public String summary() {
      return this.hostname + " -> " + this.address + " in " + this.timeMs +
        "ms";
    }

    @Override
    public JSONObject result() {
      // TODO(mdw): This is clearly not the cleanest way to do the encoding,
      // but since this is temporary code until we have the new app, it is only meant
      // to serve as a demonstration.
      JSONObject result = new JSONObject();
      JSONObject values = new JSONObject();
      JSONObject parameters = new JSONObject();
      try {
        result.put("type", this.type());
        Log.i(Speedometer.TAG, "Sending taskKey: " + taskKey);
        if (taskKey != null) result.put("task_key", taskKey);
        result.put("values", values);
        result.put("parameters", parameters);
	      parameters.put("target", this.hostname);
        values.put("summary", this.summary());
        values.put("startTime", Util.formatDate(this.startTime));
        values.put("endTime", Util.formatDate(this.endTime));
	      values.put("address", this.address);
	      values.put("realHostname", this.realHostname);
	      values.put("timeMs", this.timeMs);
      } catch (JSONException e) {
        Log.e(Speedometer.TAG, "Unable to encode result: " + e);
      }
      return result;
    }
  }

}

package com.google.wireless.speed.speedometer.measurements;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.google.wireless.speed.speedometer.MeasurementError;
import com.google.wireless.speed.speedometer.MeasurementResult;
import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.Speedometer;
import com.google.wireless.speed.speedometer.Util;

public class SimpleSpeedTest extends MeasurementTask {
  public static final String MEASUREMENT_TYPE_SIMPLESPEEDTEST =
    "SimpleSpeedTest";
  private String server;
  private static final int NUM_RTT_MEASUREMENTS = 5;
  
  public SimpleSpeedTest(String taskKey, Date startTime, Date endTime, Integer count,
      Integer interval, Integer priority, Map<String, String> params) {
    super(taskKey, startTime, endTime, count, interval, priority, params);
    this.server = params.get("server");
  }

  public static String getType() {
    return MEASUREMENT_TYPE_SIMPLESPEEDTEST;
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    Log.i(Speedometer.TAG, "speedTest started");
    DefaultHttpClient client;
    String fullurl;
    HttpGet getMethod;
    ResponseHandler<String> responseHandler;
    long rttMs, uploadKbps = 0, downloadKbps = 0;
    Date startTime = new Date();

    // Measure RTT
    try {
	    client = new DefaultHttpClient();
	    fullurl = server + "/rtttest";
	    getMethod = new HttpGet(fullurl);
	    responseHandler = new BasicResponseHandler();
	    long t1 = System.currentTimeMillis();
	    for (int i = 0; i < NUM_RTT_MEASUREMENTS; i++) {
	      @SuppressWarnings("unused")
	      String result = client.execute(getMethod, responseHandler);
	    }
	    long t2 = System.currentTimeMillis();
	    rttMs = (t2 - t1) / NUM_RTT_MEASUREMENTS;
    } catch (ClientProtocolException e) {
      throw new MeasurementError("RTT measurement failed: " + e.getMessage());
    } catch (IOException e) {
      throw new MeasurementError("RTT measurement failed: " + e.getMessage());
    }

    // Download test
    try {
	    fullurl = server + "/downloadtest";
	    getMethod = new HttpGet(fullurl);
	    long t1 = System.currentTimeMillis();
	    String result = client.execute(getMethod, responseHandler);
	    long t2 = System.currentTimeMillis();
	    long downloadBwKbps = (long) ((result.getBytes().length * 8 / 1024) /
	        ((t2 - t1) / 1000.0));
    } catch (ClientProtocolException e) {
      throw new MeasurementError("Download measurement failed: " +
          e.getMessage());
    } catch (IOException e) {
      throw new MeasurementError("Download measurement failed: " +
          e.getMessage());
    }
    
    // TODO(mdw): Implement upload measurement
    Date endTime = new Date();

    return new SimpleSpeedTestMeasurementResult(startTime, endTime,
        rttMs, downloadKbps, uploadKbps);
  }
  
  class SimpleSpeedTestMeasurementResult implements MeasurementResult {
    private Date startTime, endTime;
    private long rttMs;
    private long downloadKbps;
    private long uploadKbps;

    SimpleSpeedTestMeasurementResult(
        Date startTime, Date endTime,
        long rttMs, long downloadKbps,
        long uploadKbps) {
      this.startTime = startTime;
      this.endTime = endTime;
      this.rttMs = rttMs;
      this.downloadKbps = downloadKbps;
      this.uploadKbps = uploadKbps;
    }

    @Override
    public String type() {
      return MEASUREMENT_TYPE_SIMPLESPEEDTEST;
    }

    @Override
    public String summary() {
      return "rtt: "+ rttMs + " download: " + downloadKbps + 
        " upload: " + uploadKbps;
    }

    @Override
    public JSONObject result() {
      JSONObject result = new JSONObject();
      JSONObject values = new JSONObject();
      JSONObject parameters = new JSONObject();
      try {
        result.put("values", values);
        result.put("parameters", parameters);
        result.put("type", this.type());
        parameters.put("server", server);
        values.put("startTime", Util.formatDate(this.startTime));
        values.put("endTime", Util.formatDate(this.endTime));
        values.put("summary", this.summary());
	      values.put("rttMs", this.rttMs);
	      values.put("downloadKbps", this.downloadKbps);
	      values.put("uploadKbps", this.uploadKbps);
      } catch (JSONException e) {
        Log.e(Speedometer.TAG, "Unable to encode result: " + e);
      }
     return result;
    }
  }

}

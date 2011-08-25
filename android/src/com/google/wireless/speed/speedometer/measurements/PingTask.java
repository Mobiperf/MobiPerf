// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer.measurements;

import com.google.wireless.speed.speedometer.Config;
import com.google.wireless.speed.speedometer.MeasurementDesc;
import com.google.wireless.speed.speedometer.MeasurementError;
import com.google.wireless.speed.speedometer.MeasurementResult;
import com.google.wireless.speed.speedometer.MeasurementTask;
import com.google.wireless.speed.speedometer.R;
import com.google.wireless.speed.speedometer.SpeedometerApp;
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;
import com.google.wireless.speed.speedometer.util.RuntimeUtil;
import com.google.wireless.speed.speedometer.util.Util;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpConnectionParams;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * A callable that executes a ping task using one of three methods
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class PingTask extends MeasurementTask {
  // Type name for internal use
  public static final String TYPE = "ping";
  // Human readable name for the task
  public static final String DESCRIPTOR = "Ping";
  /* Default payload size of the ICMP packet, plus the 8-byte ICMP header resulting in a total of 
   * 64-byte ICMP packet */
  public static final int DEFAULT_PING_PACKET_SIZE = 56;
  // Default # of pings per ping task
  public static final int DEFAULT_PING_CNT_PER_TASK = 10;
  public static final double DEFAULT_PING_INTERVAL = 0.5;
  public static final int DEFAULT_PING_TIMEOUT = 10;
  /**
   * Encode ping specific parameters, along with common parameters inherited from MeasurmentDesc
   * @author wenjiezeng@google.com (Steve Zeng)
   *
   */
  public static class PingDesc extends MeasurementDesc {     
    public String pingExe = null;
    // Host address either in the numeric form or domain names
    public String target = null;
    // The payload size in bytes of the ICMP packet    
    public int packetSizeByte = PingTask.DEFAULT_PING_PACKET_SIZE;  
    public int pingTimeoutSec = PingTask.DEFAULT_PING_TIMEOUT;


    public PingDesc(String key, Date startTime,
                    Date endTime, double intervalSec, long count, long priority, 
                    Map<String, String> params) throws InvalidParameterException {
      super(PingTask.TYPE, key, startTime, endTime, intervalSec, count,
          priority, params);  
      initalizeParams(params);
      if (this.target == null || this.target.isEmpty()) {
        throw new InvalidParameterException("PingTask cannot be created due "
            + " to null target string");
      }    
    }
    
    @Override
    protected void initalizeParams(Map<String, String> params) {
      if (params == null) {
        return;
      }
      
      if (this.count == 0) {
        this.count = PingTask.DEFAULT_PING_CNT_PER_TASK;
      }
      if (this.intervalSec == 0) {
        this.intervalSec = PingTask.DEFAULT_PING_INTERVAL;
      }
      
      this.target = params.get("target");
      
      try {        
        String val = null;
        if ((val = params.get("packet_size_byte")) != null && val.length() > 0 &&
            Integer.parseInt(val) > 0) {
          this.packetSizeByte = Integer.parseInt(val);  
        }
        if ((val = params.get("ping_timeout_sec")) != null && val.length() > 0 &&
            Integer.parseInt(val) > 0) {
          this.pingTimeoutSec = Integer.parseInt(val);  
        }
      } catch (NumberFormatException e) {
        throw new InvalidParameterException("PingTask cannot be created due to invalid params");
      }
    }

    @Override
    public String getType() {
      return PingTask.TYPE;
    }  
  }
  
  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return PingDesc.class;
  }
  
  public PingTask(MeasurementDesc desc, Context parent) {
    super(new PingDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
      desc.count, desc.priority, desc.parameters), parent);
  }
  
  /**
   * Returns a copy of the PingTask
   */
  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    PingDesc newDesc = new PingDesc(desc.key, desc.startTime, desc.endTime, 
      desc.intervalSec, desc.count, desc.priority, desc.parameters);
    return new PingTask(newDesc, parent);
  }
  
  /* We will use three methods to ping the requested resource in the order of PING_COMMAND, 
   * JAVA_ICMP_PING, and HTTP_PING. If all fails, then we declare the resource unreachable */
  @Override
  public MeasurementResult call() throws MeasurementError {
    PingDesc desc = (PingDesc) measurementDesc;
    try {
      InetAddress.getByName(desc.target);
    } catch (UnknownHostException e) {
      throw new MeasurementError("Unknown host " + desc.target);
    }
    
    try {
      Log.i(SpeedometerApp.TAG, "running ping command");
      /* Prevents the phone from going to low-power mode where WiFi turns off */
      return executePingCmdTask();
    } catch (MeasurementError e) {
      try {
        Log.i(SpeedometerApp.TAG, "running java ping");
        return executeJavaPingTask();
      } catch (MeasurementError ee) {
        Log.i(SpeedometerApp.TAG, "running http ping");
        return executeHttpPingTask();
      }
    }
  }
  
  @Override
  public String getType() {
    return PingTask.TYPE;
  }
  
  @Override
  public String getDescriptor() {
    return DESCRIPTOR;
  }
  
  @Override
  public int getProgress() {
    return this.progress;
  }
  
  private MeasurementResult constructResult(ArrayList<Double> rrtVals) {
    double min = Double.MAX_VALUE;
    double max = Double.MIN_VALUE;
    double mdev, avg, filteredAvg;
    double total = 0;
    boolean success = true;
    
    if (rrtVals.size() == 0) {
      return null;
    }
    
    for (double rrt : rrtVals) {
      if (rrt < min) {
        min = rrt;
      }
      if (rrt > max) {
        max = rrt;
      }
      total += rrt;
    }
    
    avg = total / rrtVals.size();
    mdev = Util.getStandardDeviation(rrtVals, avg);
    filteredAvg = filterPingResults(rrtVals, avg);
    
    MeasurementResult result = new MeasurementResult(RuntimeUtil.getDeviceInfo().deviceId, 
        RuntimeUtil.getDeviceProperty(), PingTask.TYPE, Calendar.getInstance().getTime(), 
        success, this.measurementDesc);
    
    result.addResult("mean_rtt_ms", avg);
    result.addResult("min_rtt_ms", min);
    result.addResult("max_rtt_ms", max);
    result.addResult("stddev_rtt_ms", mdev);
    if (filteredAvg != avg) {
      result.addResult("filtered_mean_rtt_ms", filteredAvg);
    }
    result.addResult("packet_loss", 1 - 
        ((double) rrtVals.size() / (double) Config.PING_COUNT_PER_MEASUREMENT));
    
    return result;
  }

  private void cleanUp(Process proc) {
    if (proc != null) {
      proc.destroy();
    }      
  }
  
  /* Compute the average of the filtered rtts.
   * The first several ping results are usually extremely large as the device needs to activate
   * the wireless interface and resolve domain names. Such distorted measurements are filtered out
   * 
   */
  private double filterPingResults(final ArrayList<Double> rrts, double avg) {
    double rrtAvg = avg;
    // Our # of results should be less than the # of times we ping
    try {
      ArrayList<Double> filteredResults =
          Util.applyInnerBandFilter(rrts, Double.MIN_VALUE, rrtAvg * Config.PING_FILTER_THRES);
      // Now we compute the average again based on the filtered results
      if (filteredResults != null && filteredResults.size() > 0) {
        rrtAvg = Util.getSum(filteredResults) / filteredResults.size();
      }
    } catch (InvalidParameterException e) {
      Log.wtf(SpeedometerApp.TAG, "This should never happen because rrts is never empty");
    }
    return rrtAvg;
  } 
  
  // Runs when SystemState is IDLE
  private MeasurementResult executePingCmdTask() throws MeasurementError {
    PingDesc pingTask = (PingDesc) this.measurementDesc;
    Process pingProc = null;
    String errorMsg = "";
    MeasurementResult measurementResult = null;
    // TODO(Wenjie): Add a exhaustive list of ping locations for different Android phones
    pingTask.pingExe = parent.getString(R.string.ping_executable);
    try {
      String command = Util.constructCommand(pingTask.pingExe, "-i", pingTask.intervalSec,
          "-s", pingTask.packetSizeByte, "-w", pingTask.pingTimeoutSec, "-c", 
          Config.PING_COUNT_PER_MEASUREMENT, pingTask.target);
      pingProc = Runtime.getRuntime().exec(command);
      
      // Grab the output of the process that runs the ping command
      InputStream is = pingProc.getInputStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(is));

      String line = null;
      int lineCnt = 0;
      ArrayList<Double> rrts = new ArrayList<Double>();
      // Process each line of the ping output and store the rrt in array rrts.
      while ((line = br.readLine()) != null) {
        // Ping prints a number of 'param=value' pairs, among which we only need the 
        // 'time=rrt_val' pair
        String[] pairs = line.split(" ");
        int len = pairs.length;
        for (int  i = 0; i < len; i++) {
          if (pairs[i].startsWith("time")) {
            String[] tokens = pairs[i].split("=");
            if (tokens.length == 2) {
              // NumberFormatException handled in the end
              double rrtVal = Double.parseDouble(tokens[1].trim());
              rrts.add(rrtVal);
            } else {
              Log.i(SpeedometerApp.TAG, "ping output " + pairs[i] + 
                  " is not in the format of param=value");
            }
          }
        }
        this.progress = 100 * ++lineCnt / Config.PING_COUNT_PER_MEASUREMENT;
        broadcastProgressForUser(progress);
        Log.i(SpeedometerApp.TAG, line);
      }     
      measurementResult = constructResult(rrts);
      Log.i(SpeedometerApp.TAG, MeasurementJsonConvertor.toJsonString(measurementResult));
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (SecurityException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (NumberFormatException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";  
    } catch (InvalidParameterException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } finally {
      // All associated streams with the process will be closed upon destroy()
      cleanUp(pingProc);
    }
    
    if (measurementResult == null) {
      throw new MeasurementError(errorMsg);
    }
    return measurementResult;
  }

  // Runs when the ping command fails
  private MeasurementResult executeJavaPingTask() throws MeasurementError {
    PingDesc pingTask = (PingDesc) this.measurementDesc;
    long pingStartTime = 0;
    long pingEndTime = 0;
    ArrayList<Double> rrts = new ArrayList<Double>();
    String errorMsg = "";

    try {       
      int timeOut = (int) (1000 * (double) pingTask.pingTimeoutSec /
            Config.PING_COUNT_PER_MEASUREMENT);
      int successfulPingCnt = 0;
      long totalPingDelay = 0;
      for (int i = 0; i < Config.PING_COUNT_PER_MEASUREMENT; i++) {
        pingStartTime = System.currentTimeMillis();
        boolean status = InetAddress.getByName(pingTask.target).isReachable(timeOut);
        pingEndTime = System.currentTimeMillis();
        long rrtVal = pingEndTime - pingStartTime;
        if (status) {
          totalPingDelay += rrtVal;
          rrts.add((double) rrtVal);
        }
        this.progress = 100 * i / Config.PING_COUNT_PER_MEASUREMENT;
        broadcastProgressForUser(progress);
      }
      Log.i(SpeedometerApp.TAG, "java ping succeeds");
      return constructResult(rrts);        
    } catch (IllegalArgumentException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } 
    Log.i(SpeedometerApp.TAG, "java ping fails");
    throw new MeasurementError(errorMsg);
  }
  
  /** 
   * Use the HTTP Head method to emulate ping. The measurement from this method can be 
   * substantially (2x) greater than the first two methods and inaccurate. This is because, 
   * depending on the implementing of the destination web server, either a quick HTTP
   * response is replied or some actual heavy lifting will be done in preparing the response
   * */
  private MeasurementResult executeHttpPingTask() throws MeasurementError {
    long pingStartTime = 0;
    long pingEndTime = 0;
    ArrayList<Double> rrts = new ArrayList<Double>();
    PingDesc pingTask = (PingDesc) this.measurementDesc;
    String errorMsg = "";

    try {
      long totalPingDelay = 0;
      
      HttpClient client = AndroidHttpClient.newInstance(Util.prepareUserAgent(this.parent));
      HttpHead headMethod = new HttpHead("http://" + pingTask.target);
      headMethod.addHeader(new BasicHeader("Connection", "close"));
      headMethod.setParams(new BasicHttpParams().setParameter(
          CoreConnectionPNames.CONNECTION_TIMEOUT, 1000));
      
      int timeOut = (int) (1000 * (double) pingTask.pingTimeoutSec /
          Config.PING_COUNT_PER_MEASUREMENT);
      HttpConnectionParams.setConnectionTimeout(headMethod.getParams(), timeOut);
                      
      for (int i = 0; i < Config.PING_COUNT_PER_MEASUREMENT; i++) {
        pingStartTime = System.currentTimeMillis();
        HttpResponse response = client.execute(headMethod);  
        pingEndTime = System.currentTimeMillis();
        rrts.add((double) (pingEndTime - pingStartTime));
        this.progress = 100 * i / Config.PING_COUNT_PER_MEASUREMENT;
        broadcastProgressForUser(progress);
      }
      Log.i(SpeedometerApp.TAG, "HTTP get ping succeeds");
      return constructResult(rrts);
    } catch (MalformedURLException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, e.getMessage());
      errorMsg += e.getMessage() + "\n";
    }
    Log.i(SpeedometerApp.TAG, "HTTP get ping fails");
    throw new MeasurementError(errorMsg);
  }
}


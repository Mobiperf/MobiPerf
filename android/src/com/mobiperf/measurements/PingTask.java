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

import android.content.Context;
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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;
import com.mobiperf.util.Util;
import com.mobiperf.Config;
import com.mobiperf.Logger;
import com.mobiperf.MeasurementDesc;
import com.mobiperf.MeasurementError;
import com.mobiperf.MeasurementResult;
import com.mobiperf.MeasurementTask;
import com.mobiperf.R;
import com.mobiperf.SpeedometerApp;

/**
 * A callable that executes a ping task using one of three methods
 */
public class PingTask extends MeasurementTask {
  // Type name for internal use
  public static final String TYPE = "ping";
  // Human readable name for the task
  public static final String DESCRIPTOR = "ping";
  /* Default payload size of the ICMP packet, plus the 8-byte ICMP header resulting in a total of 
   * 64-byte ICMP packet */
  public static final int DEFAULT_PING_PACKET_SIZE = 56;
  public static final int DEFAULT_PING_TIMEOUT = 10;
  
  private Process pingProc = null;
  private String PING_METHOD_CMD  = "ping_cmd";
  private String PING_METHOD_JAVA = "java_ping";
  private String PING_METHOD_HTTP = "http";
  private String targetIp = null;
  
  // Track data consumption for this task to avoid exceeding user's limit  
  private long dataConsumed;
  
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
      initializeParams(params);
      if (this.target == null || this.target.length() == 0) {
        throw new InvalidParameterException("PingTask cannot be created due "
            + " to null target string");
      }    
    }
    
    @Override
    protected void initializeParams(Map<String, String> params) {
      if (params == null) {
        return;
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
    dataConsumed = 0;
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
    int ipByteLength;
    try {
      InetAddress addr = InetAddress.getByName(desc.target);
      // Get the address length
      ipByteLength = addr.getAddress().length;
      Logger.i("IP address length is " + ipByteLength);
      // All ping methods ping against targetIp rather than desc.target
      targetIp = addr.getHostAddress();
      Logger.i("IP is " + targetIp);
    } catch (UnknownHostException e) {
      throw new MeasurementError("Unknown host " + desc.target);
    }
    
    try {
      Logger.i("running ping command");
      // Prevents the phone from going to low-power mode where WiFi turns off
      return executePingCmdTask(ipByteLength);
    } catch (MeasurementError e) {
      try {
        Logger.i("running java ping");
        return executeJavaPingTask();
      } catch (MeasurementError ee) {
        Logger.i("running http ping");
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
  
  private MeasurementResult constructResult(ArrayList<Double> rrtVals, double packetLoss,
                                            int packetsSent, String pingMethod) {
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
    
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    
    MeasurementResult result = new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
        phoneUtils.getDeviceProperty(), PingTask.TYPE, System.currentTimeMillis() * 1000,
        success, this.measurementDesc);
    
    result.addResult("target_ip", targetIp);
    result.addResult("mean_rtt_ms", avg);
    result.addResult("min_rtt_ms", min);
    result.addResult("max_rtt_ms", max);
    result.addResult("stddev_rtt_ms", mdev);
    if (filteredAvg != avg) {
      result.addResult("filtered_mean_rtt_ms", filteredAvg);
    }
    result.addResult("packet_loss", packetLoss);
    result.addResult("packets_sent", packetsSent);
    result.addResult("ping_method", pingMethod);
    Logger.i(MeasurementJsonConvertor.toJsonString(result));
    return result;
  }
  
  private void cleanUp(Process proc) {
    try { 
      if (proc != null) {
        proc.destroy();
      }
    } catch (Exception e) { 
      Logger.w("Unable to kill ping process" + e.getMessage());
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
  private MeasurementResult executePingCmdTask(int ipByteLen) throws MeasurementError {
    Logger.i("Starting executePingCmdTask");
    PingDesc pingTask = (PingDesc) this.measurementDesc;
    String errorMsg = "";
    MeasurementResult measurementResult = null;
    // TODO(Wenjie): Add a exhaustive list of ping locations for different Android phones
    pingTask.pingExe = Util.pingExecutableBasedOnIPType(ipByteLen, parent);
    Logger.i("Ping executable is " + pingTask.pingExe);
    if (pingTask.pingExe == null) {
      Logger.e("Ping executable not found");
      throw new MeasurementError("Ping executable not found");
    }
    try {
      String command = Util.constructCommand(pingTask.pingExe, "-i", 
          Config.DEFAULT_INTERVAL_BETWEEN_ICMP_PACKET_SEC,
          "-s", pingTask.packetSizeByte, "-w", pingTask.pingTimeoutSec, "-c", 
          Config.PING_COUNT_PER_MEASUREMENT, targetIp);
      Logger.i("Running: " + command);
      pingProc = Runtime.getRuntime().exec(command);
      
      dataConsumed += pingTask.packetSizeByte * Config.PING_COUNT_PER_MEASUREMENT * 2;
      
      // Grab the output of the process that runs the ping command
      InputStream is = pingProc.getInputStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(is));

      String line = null;
      int lineCnt = 0;
      ArrayList<Double> rrts = new ArrayList<Double>();
      ArrayList<Integer> receivedIcmpSeq = new ArrayList<Integer>();
      double packetLoss = Double.MIN_VALUE;
      int packetsSent = Config.PING_COUNT_PER_MEASUREMENT;
      // Process each line of the ping output and store the rrt in array rrts.
      while ((line = br.readLine()) != null) {
        // Ping prints a number of 'param=value' pairs, among which we only need the 
        // 'time=rrt_val' pair
        String[] extractedValues = Util.extractInfoFromPingOutput(line);
        if (extractedValues != null) {
          int curIcmpSeq = Integer.parseInt(extractedValues[0]);
          double rrtVal = Double.parseDouble(extractedValues[1]);
  
          // ICMP responses from the system ping command could be duplicate and out of order
          if (!receivedIcmpSeq.contains(curIcmpSeq)) {
            rrts.add(rrtVal);
            receivedIcmpSeq.add(curIcmpSeq);
          }
        }
        
        this.progress = 100 * ++lineCnt / Config.PING_COUNT_PER_MEASUREMENT;
        this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, progress);
        broadcastProgressForUser(progress);
        // Get the number of sent/received pings from the ping command output 
        int[] packetLossInfo = Util.extractPacketLossInfoFromPingOutput(line);
        if (packetLossInfo != null) {
          packetsSent = packetLossInfo[0];
          int packetsReceived = packetLossInfo[1];
          packetLoss = 1 - ((double) packetsReceived / (double) packetsSent);
        }
        
        Logger.i(line);
      }
      // Use the output from the ping command to compute packet loss. If that's not
      // available, use an estimation.
      if (packetLoss == Double.MIN_VALUE) {
        packetLoss = 1 - ((double) rrts.size() / (double) Config.PING_COUNT_PER_MEASUREMENT);
      }
      measurementResult = constructResult(rrts, packetLoss, packetsSent, PING_METHOD_CMD);
    } catch (IOException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (SecurityException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (NumberFormatException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";  
    } catch (InvalidParameterException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } finally {
      // All associated streams with the process will be closed upon destroy()
      cleanUp(pingProc);
    }
    
    if (measurementResult == null) {
      Logger.e("Error running ping: " + errorMsg);
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
    MeasurementResult result = null;

    try {       
      int timeOut = (int) (3000 * (double) pingTask.pingTimeoutSec /
            Config.PING_COUNT_PER_MEASUREMENT);
      int successfulPingCnt = 0;
      long totalPingDelay = 0;
      for (int i = 0; i < Config.PING_COUNT_PER_MEASUREMENT; i++) {
        pingStartTime = System.currentTimeMillis();
        boolean status = InetAddress.getByName(targetIp).isReachable(timeOut);
        pingEndTime = System.currentTimeMillis();
        long rrtVal = pingEndTime - pingStartTime;
        if (status) {
          totalPingDelay += rrtVal;
          rrts.add((double) rrtVal);
        }
        this.progress = 100 * i / Config.PING_COUNT_PER_MEASUREMENT;
        broadcastProgressForUser(progress);
      }
      Logger.i("java ping succeeds");
      double packetLoss = 1 - ((double) rrts.size() / (double) Config.PING_COUNT_PER_MEASUREMENT);
      
      dataConsumed += pingTask.packetSizeByte * Config.PING_COUNT_PER_MEASUREMENT * 2;
      
      result = constructResult(rrts, packetLoss, Config.PING_COUNT_PER_MEASUREMENT, PING_METHOD_JAVA);
    } catch (IllegalArgumentException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (IOException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } 

    if (result != null) {
      return result;
    } else {
      Logger.i("java ping fails");
      throw new MeasurementError(errorMsg);
    }
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
    MeasurementResult result = null;
    
    try {
      long totalPingDelay = 0;
      
      URL url = new URL("http://"+ pingTask.target);
      
      int timeOut = (int) (3000 * (double) pingTask.pingTimeoutSec /
          Config.PING_COUNT_PER_MEASUREMENT);
                      
      for (int i = 0; i < Config.PING_COUNT_PER_MEASUREMENT; i++) {
        pingStartTime = System.currentTimeMillis();
        HttpURLConnection httpClient = (HttpURLConnection) url.openConnection();
        httpClient.setRequestProperty("Connection", "close");
        httpClient.setRequestMethod("HEAD");
        httpClient.setReadTimeout(timeOut);
        httpClient.setConnectTimeout(timeOut);
        httpClient.connect();
        pingEndTime = System.currentTimeMillis();
        httpClient.disconnect();
        rrts.add((double) (pingEndTime - pingStartTime));
        this.progress = 100 * i / Config.PING_COUNT_PER_MEASUREMENT;
        broadcastProgressForUser(progress);
      }
      Logger.i("HTTP get ping succeeds");
      Logger.i("RTT is " + rrts.toString());
      double packetLoss = 1 - ((double) rrts.size() / (double) Config.PING_COUNT_PER_MEASUREMENT);
      result = constructResult(rrts, packetLoss, Config.PING_COUNT_PER_MEASUREMENT, PING_METHOD_HTTP);
      dataConsumed += pingTask.packetSizeByte * Config.PING_COUNT_PER_MEASUREMENT * 2;
      
    } catch (MalformedURLException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";
    } catch (IOException e) {
      Logger.e(e.getMessage());
      errorMsg += e.getMessage() + "\n";
    }
    if (result != null) {
      return result;
    } else {
      Logger.i("HTTP get ping fails");
      throw new MeasurementError(errorMsg);
    }
  }
  
  @Override
  public String toString() {
    PingDesc desc = (PingDesc) measurementDesc;
    return "[Ping]\n  Target: " + desc.target + "\n  Interval (sec): " + desc.intervalSec 
        + "\n  Next run: " + desc.startTime;
  }
  
  @Override
  public void stop() {
    cleanUp(pingProc);
  }

  
  /**
   * Data sent so far by this task.
   * 
   * We count packets sent directly to calculate the data sent
   */
  @Override
  public long getDataConsumed() {
    return dataConsumed;
  }
}

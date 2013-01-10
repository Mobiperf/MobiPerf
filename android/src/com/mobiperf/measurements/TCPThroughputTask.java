// Copyright 2012 RobustNet Lab, University of Michigan. All Rights Reserved.

package com.mobiperf.measurements;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Random;

import com.mobiperf.Config;
import com.mobiperf.Logger;
import com.mobiperf.MeasurementDesc;
import com.mobiperf.MeasurementError;
import com.mobiperf.MeasurementResult;
import com.mobiperf.MeasurementTask;
import com.mobiperf.util.MLabNS;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;

import android.content.Context;

/**
 * @author Haokun Luo
 * 
 * TCP Throughput is a measurement task for cellular network throughput.
 * 1. Uplink: the mobile device continuously sends packets with consistent packet size.
 *    We send packets for a fixed amount of time, and we sample each throughput value
 *    at a smaller period. We use the median of all the sampling result as the final
 *    measurement result. The result is calculated at the server side, and send back
 *    to the device.
 * 2. Downlink: similar methodology as uplink. Only difference is that the device is
 *    receiving packets from the server, and calculate the result locally. 
 */
public class TCPThroughputTask extends MeasurementTask {
  // default constant here
  public static final String DESCRIPTOR = "TCP Speed Test";
  public static final int PORT_DOWNLINK = 6001;
  public static final int PORT_UPLINK = 6002;
  public static final int PORT_CONFIG = 6003;
  public static final String TYPE = "tcpthroughput";

  // Timing related
  public final int BUFFER_SIZE = 5000;
  public static final long DURATION_IN_MILLI = 15000;
  public static final long SAMPLE_PERIOD_IN_MILLI = 1000; 
  public static final long SLOW_START_PERIOD_IN_MILLI = 5000;
  public static final int TCP_TIMEOUT_IN_MILLI = 30000;
  // largest non-fragment packet size in LTE (uplink)
  public static final int THROUGHPUT_UP_PKT_SIZE_MAX = 1357;
  public static final int THROUGHPUT_UP_PKT_SIZE_MIN = 700;

  // Data related
  private static final int KBYTE = 1024;
  private static final int DATA_LIMIT_BYTE_UP = 5*KBYTE*KBYTE; 
  private static final int DATA_LIMIT_BYTE_DOWN = 10*KBYTE*KBYTE;
  private boolean DATA_LIMIT_ON = true;
  private boolean DATA_LIMIT_EXCEEDED = false;
  private static final String UPLINK_FINISH_MSG = "*";

  private Context context = null;

  // helper variables 
  private int accumulativeSize = 0;
  private int MAXPROGRESS = Config.MAX_PROGRESS_BAR_VALUE;
  private Random randStr = new Random();
  private ArrayList<Double> samplingResults = new ArrayList<Double>();
  //start time of each sampling period
  private long startSampleTime = 0;
  private String serverVersion = "";
  private long taskStartTime = 0;
  private double taskDuration = 0;
  //uplink accumulative data
  private int totalSendSize = 0;
  // downlink accumulative data
  private int totalRevSize = 0;

  // class constructor
  public TCPThroughputTask(MeasurementDesc desc, Context context) {
    super(new TCPThroughputDesc(desc.key, desc.startTime, desc.endTime, 
          desc.intervalSec, desc.count, desc.priority, desc.parameters, context),
          context);
    this.context = context;
    Logger.i("Create new throughput task");
  }

  /**
   * There are seven parameters specifically for this experiment:
   * 1. data_limit_mb_up: uplink cellular network data limit
   * 2. data_limit_mb_down: downlink cellular network data limit
   * 3. duration_period_sec : downlink maximum experiment duration period
   * 4. pkt_size_up_bytes: the size each packet in the uplink
   * 5. sample_period_sec : the small interval to calculate current throughput result
   * 6. slow_start_period_sec : waiting period to avoid TCP slow start
   * 7. tcp_timeout_sec: TCP connection timeout
   */
  
  public static class TCPThroughputDesc extends MeasurementDesc {
    // declared parameters
    public int     data_limit_bytes_up = TCPThroughputTask.DATA_LIMIT_BYTE_UP;
    public int     data_limit_bytes_down = TCPThroughputTask.DATA_LIMIT_BYTE_DOWN;
    public boolean dirUp = false;
    public long    duration_period_ms = TCPThroughputTask.DURATION_IN_MILLI;
    public int     pkt_size_up_bytes = TCPThroughputTask.THROUGHPUT_UP_PKT_SIZE_MAX;
    public int     port_uplink = TCPThroughputTask.PORT_UPLINK;
    public int     port_downlink = TCPThroughputTask.PORT_DOWNLINK;
    public int     port_config = TCPThroughputTask.PORT_CONFIG;
    public long    sample_period_ms = TCPThroughputTask.SAMPLE_PERIOD_IN_MILLI;
    public long    slow_start_period_ms = TCPThroughputTask.SLOW_START_PERIOD_IN_MILLI;
    public String  target = null;
    public int     tcp_timeout_ms = TCPThroughputTask.TCP_TIMEOUT_IN_MILLI;
    public String  uplink_finish_msg = TCPThroughputTask.UPLINK_FINISH_MSG;

    private Context context = null;

    public TCPThroughputDesc(String key, Date startTime,
                             Date endTime, double intervalSec, long count, 
                             long priority, Map<String, String> params,
                             Context context) 
                             throws InvalidParameterException {
      super(TCPThroughputTask.TYPE, key, startTime, endTime, intervalSec, count,
            priority, params);
      this.context = context;

      initializeParams(params);
      if (this.target == null || this.target.length() == 0) {
        throw new InvalidParameterException("TCPThroughputTask null target");
      }
    }

    @Override
    protected void initializeParams(Map<String, String> params) {
      if (params == null) {
        return;
      }

      this.target = params.get("target");
      if (!this.target.equals(MLabNS.TARGET)) {
        Logger.i("Not using MLab server!");
        throw new InvalidParameterException("Unknown target " + target +
        		" for TCPThroughput");
      }

      this.target = MLabNS.Lookup(context, "mobiperf");
      Logger.i("Setting target to: " + this.target);

      try {
        String readVal = null;
        // TODO (Haokun): get limitation from configuration
        if ((readVal = params.get("data_limit_mb_down")) != null && readVal.length() > 0 
             && Integer.parseInt(readVal) > 0) {
          this.data_limit_bytes_down = Integer.parseInt(readVal)*
                                       TCPThroughputTask.KBYTE*
                                       TCPThroughputTask.KBYTE;
          if (this.data_limit_bytes_down > TCPThroughputTask.DATA_LIMIT_BYTE_DOWN) {
            this.data_limit_bytes_down = TCPThroughputTask.DATA_LIMIT_BYTE_DOWN;
          }
        }

        // TODO (Haokun): get limitation from configuration
        if ((readVal = params.get("data_limit_mb_up")) != null && readVal.length() > 0 
             && Integer.parseInt(readVal) > 0) {
          this.data_limit_bytes_up = Integer.parseInt(readVal)*
                                     TCPThroughputTask.KBYTE*
                                     TCPThroughputTask.KBYTE;
          if (this.data_limit_bytes_up > TCPThroughputTask.DATA_LIMIT_BYTE_UP) {
            this.data_limit_bytes_up = TCPThroughputTask.DATA_LIMIT_BYTE_UP;
          }
        }
        if ((readVal = params.get("duration_period_sec")) != null && readVal.length() > 0 
             && Integer.parseInt(readVal) > 0) {
          this.duration_period_ms = (long)Integer.parseInt(readVal)*1000;
          if (this.duration_period_ms > TCPThroughputTask.DURATION_IN_MILLI) {
            this.duration_period_ms = TCPThroughputTask.DURATION_IN_MILLI;
          }
        }
        if ((readVal = params.get("pkt_size_up_bytes")) != null && readVal.length() > 0 
             && Integer.parseInt(readVal) > 0) {
          this.pkt_size_up_bytes = Integer.parseInt(readVal);
          if (this.pkt_size_up_bytes > TCPThroughputTask.THROUGHPUT_UP_PKT_SIZE_MAX) {
            this.pkt_size_up_bytes = TCPThroughputTask.THROUGHPUT_UP_PKT_SIZE_MAX;
          }
          if (this.pkt_size_up_bytes < TCPThroughputTask.THROUGHPUT_UP_PKT_SIZE_MIN) {
            this.pkt_size_up_bytes = TCPThroughputTask.THROUGHPUT_UP_PKT_SIZE_MIN;
          }
        }
        if ((readVal = params.get("sample_period_sec")) != null && readVal.length() > 0 
             && Integer.parseInt(readVal) > 0) {
          this.sample_period_ms = (long)Integer.parseInt(readVal)*1000;
          if (this.sample_period_ms > TCPThroughputTask.DURATION_IN_MILLI/2) {
            this.sample_period_ms = TCPThroughputTask.DURATION_IN_MILLI/2;
          }
        }
        if ((readVal = params.get("slow_start_period_sec")) != null
             && readVal.length() > 0 && Integer.parseInt(readVal) > 0) {
          this.slow_start_period_ms = (long)Integer.parseInt(readVal)*1000;
          if (this.slow_start_period_ms > TCPThroughputTask.DURATION_IN_MILLI/2) {
            this.slow_start_period_ms = TCPThroughputTask.DURATION_IN_MILLI/2;
          }
        }
        if ((readVal = params.get("tcp_timeout_sec")) != null && readVal.length() > 0 
             && Integer.parseInt(readVal) > 0) {
          this.tcp_timeout_ms = Integer.parseInt(readVal)*1000;
          if (this.tcp_timeout_ms > TCPThroughputTask.TCP_TIMEOUT_IN_MILLI) {
            this.tcp_timeout_ms = TCPThroughputTask.TCP_TIMEOUT_IN_MILLI;
          }
        }
      } catch  (NumberFormatException e) {
        throw new InvalidParameterException("TCP Throughput Task invalid parameters.");
      }

      String dir = null;
      if ((dir = params.get("direction")) != null && dir.length() > 0) {
        if (dir.compareTo("Up") == 0) {
          this.dirUp = true;
        }
      }
    }

    @Override
    public String getType() {
      return TCPThroughputTask.TYPE;
    }

    /**
       * Find the median value from a TCPThroughput JSON result string (already sorted)
       * Suppose N is the number of results. If N is odd, we pick the result with index
       * (N-1)/2. If N is even, we take the mean value between index N/2 and N/2-1
       */
    public double calMedianSpeedFromTCPThroughputOutput(String outputInJSON) {
      if (outputInJSON == null || 
          outputInJSON.equals("") || 
          outputInJSON.charAt(0) != '[' || 
          outputInJSON.charAt(outputInJSON.length()-1) != ']') {
        return 0.0;
      }

      String[] splitResult = outputInJSON.split(",");
      int resultLen = splitResult.length;
      double result = 0.0;
      if (resultLen % 2 == 0) {
        result = (Double.parseDouble(splitResult[resultLen / 2]) +
                 Double.parseDouble(splitResult[resultLen / 2 + 1])) / 2;
      } else {
        result = Double.parseDouble(splitResult[(resultLen - 1) / 2]);
      }
      return result;
    }
  }

  /**
   * Make a deep cloning of the task
   */
  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    TCPThroughputDesc newDesc = new TCPThroughputDesc(
                                desc.key, desc.startTime, 
                                desc.endTime, desc.intervalSec, desc.count, desc.priority,
                                desc.parameters, this.context);
    return new TCPThroughputTask(newDesc, this.context);
  }

  @Override
  public String getType() {
    return TCPThroughputTask.TYPE;
  }

  @Override
  public String getDescriptor() {
    return TCPThroughputTask.DESCRIPTOR;
  }

  /** 
   * This will be printed to the device log console. Make sure it's well structured and
   * human readable
   */
  @Override
  public String toString() {
    TCPThroughputDesc desc = (TCPThroughputDesc) measurementDesc;
    String resp;

    if (desc.dirUp) {
      resp = "[TCP Uplink]\n";
    } else {
      resp = "[TCP Downlink]\n";
    }
 
    resp += " Target: " + desc.target + "\n  Interval (sec): " + 
    desc.intervalSec + "\n  Next run: " + desc.startTime;

    return resp;
  }

  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return TCPThroughputDesc.class;
  }

  @Override
  public void stop() {
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    boolean isMeasurementSuccessful = false;
    TCPThroughputDesc desc = (TCPThroughputDesc)measurementDesc;
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();

    // reset the data limit if the phone is under Wifi
    if (phoneUtils.getNetwork().equals(phoneUtils.NETWORK_WIFI)) {
      Logger.i("Detect Wifi network");
      this.DATA_LIMIT_ON = false;
    }

    Logger.i("Running TCPThroughput on " + desc.target);
    try {
      // fetch server information
      if (!acquireServerConfig()) {
        throw new MeasurementError("Fail to acquire server configuration");
      }
      Logger.w("Server version is " + this.serverVersion);
      if (desc.dirUp == true) {
        uplink();
        Logger.i("Uplink measurement result is:");
      }
      else {
        this.taskStartTime = System.currentTimeMillis();
        downlink();
        Logger.i("Downlink measurement result is:");
      }
      isMeasurementSuccessful = true;
    } catch (MeasurementError e) {
      throw e;
    } catch (IOException e) {
      Logger.e("Error close the socket for " + desc.type);
      throw new MeasurementError("Error close the socket for " + desc.type);
    } catch (InterruptedException e) {
      Logger.e("Interrupted captured");
      throw new MeasurementError("Task gets interrrupted");
    }

    MeasurementResult result = new MeasurementResult(
                               phoneUtils.getDeviceInfo().deviceId,
                               phoneUtils.getDeviceProperty(), TCPThroughputTask.TYPE,
                               System.currentTimeMillis() * 1000, isMeasurementSuccessful,
                               this.measurementDesc);
    // TODO (Haokun): add more results if necessary
    String resultList = MeasurementJsonConvertor.toJsonString(this.samplingResults);
    String limitExceed = MeasurementJsonConvertor.toJsonString(this.DATA_LIMIT_EXCEEDED);
    String taskDur = MeasurementJsonConvertor.toJsonString(this.taskDuration);
    Logger.w("Result for this round is " + resultList);
    Logger.w("Data limit exceed is " + limitExceed);
    Logger.w("Total task duration is " + taskDur);
    result.addResult("tcp_speed_results", this.samplingResults);
    result.addResult("data_limit_exceeded", this.DATA_LIMIT_EXCEEDED);
    result.addResult("duration", this.taskDuration);
    result.addResult("server_version", this.serverVersion);
    return result;
  }
  
  /*****************************************************************
   * Core measurement functions definitions
   *****************************************************************
   * acquire server configuration information
   * 1) m-lab slice version
   * 
   * @return: true -- successful acquire data from M-Lab slice
   * @return: false -- failure to acquire data from M-Lab slice
   */
  private boolean acquireServerConfig() throws MeasurementError, IOException,
                                            InterruptedException {
    Socket tcpSocket = null;
    InputStream iStream = null;
    boolean result = false;
    try {
      tcpSocket = new Socket();
      buildUpSocket(tcpSocket, ((TCPThroughputDesc)measurementDesc).target, 
                   ((TCPThroughputDesc)measurementDesc).port_config);
      iStream = tcpSocket.getInputStream();
    } catch (IOException e) {
      throw new MeasurementError("Error open uplink socket at " + 
                                ((TCPThroughputDesc)measurementDesc).target + 
                                " with port " +
                                ((TCPThroughputDesc)measurementDesc).port_config); 
    }
    
    try {
      // read from server side configuration
      byte [] resultMsg = new byte[this.BUFFER_SIZE];
      int resultMsgLen = iStream.read(resultMsg, 0, resultMsg.length);
      if (resultMsgLen > 0) {
        // TODO(Haokun): Maybe switch to JSON for multiple acquired data 
        //               currently use one double number
        this.serverVersion = new String(resultMsg).substring(0, resultMsgLen);
        result = true;
      }
    } catch (IOException e) {
      throw new MeasurementError("Error to acquire configuration from " +
                                ((TCPThroughputDesc)measurementDesc).target);
    } finally {
      iStream.close();
      tcpSocket.close();
      Logger.i("Close server Config socket");
    }
    return result;
  }
  
  /* Uplink measurement task
   * @throws IOException 
   * @throws InterruptedException 
   */
  private void uplink() throws MeasurementError, IOException, InterruptedException {
    Logger.i("Start uplink task on " + ((TCPThroughputDesc)measurementDesc).target);
    Socket tcpSocket = null;
    InputStream iStream = null;
    OutputStream oStream = null;

    try {
      tcpSocket = new Socket();
      buildUpSocket(tcpSocket, ((TCPThroughputDesc)measurementDesc).target, 
                   ((TCPThroughputDesc)measurementDesc).port_uplink);
      oStream = tcpSocket.getOutputStream();
      iStream = tcpSocket.getInputStream();
    } catch (IOException e){
      e.printStackTrace();
      throw new MeasurementError("Error open uplink socket at " + 
                                ((TCPThroughputDesc)measurementDesc).target + 
                                " with port " +
                                ((TCPThroughputDesc)measurementDesc).port_uplink);
    }

    long startTime = System.currentTimeMillis();
    long endTime = startTime;

    byte[] uplinkBuffer = new byte[((TCPThroughputDesc)measurementDesc).pkt_size_up_bytes];
    this.genRandomByteArray(uplinkBuffer);
    try {
      int progUpdateCount = 0;
      long totalDuration = ((TCPThroughputDesc)measurementDesc).duration_period_ms +
                           ((TCPThroughputDesc)measurementDesc).slow_start_period_ms;
      do {
        oStream.write(uplinkBuffer, 0, uplinkBuffer.length);
        oStream.flush();
        endTime = System.currentTimeMillis();

        this.totalSendSize += ((TCPThroughputDesc)measurementDesc).pkt_size_up_bytes;
        if (this.DATA_LIMIT_ON &&
            this.totalSendSize >= 
            ((TCPThroughputDesc)measurementDesc).data_limit_bytes_up) {
          Logger.i("Detect uplink exceeding limitation " +
                  (double)((TCPThroughputDesc)measurementDesc).data_limit_bytes_up /
                  (1024*1024) + " MB");
          this.DATA_LIMIT_EXCEEDED = true;
          break;
        }
        this.progress = (int)(this.MAXPROGRESS * (endTime - startTime) / totalDuration);
        this.progress = Math.min(this.progress, this.MAXPROGRESS);
        // propagate every quarter
        if (this.progress >= (progUpdateCount+1)*25) {
          broadcastProgressForUser(this.progress);
          progUpdateCount++;
        }
      } while ((endTime - startTime) < totalDuration);
      
      // convert into seconds
      this.taskDuration = (double)(endTime - startTime) / 1000.0;
      Logger.i("Uplink total data comsumption is " + 
              (double)this.totalSendSize/(1024*1024) + " MB");
      // send last message with special content
      uplinkBuffer = ((TCPThroughputDesc)measurementDesc).uplink_finish_msg.getBytes();
      oStream.write(uplinkBuffer, 0, uplinkBuffer.length);
      oStream.flush();
      // read from server side results
      byte [] resultMsg = new byte[this.BUFFER_SIZE];
      int resultMsgLen = iStream.read(resultMsg, 0, resultMsg.length);
      if (resultMsgLen > 0) {
        String resultMsgStr = new String(resultMsg).substring(0, resultMsgLen);
        // Sample result string is "1111.11#2222.22#3333.33";
        Logger.i("Uplink result from server is " + resultMsgStr);
        String [] tps_result_str = resultMsgStr.split("#");
        double sampleResult;
        for (int i = 0; i < tps_result_str.length; i++) {
          sampleResult = Double.valueOf(tps_result_str[i]);
          this.samplingResults = this.insertWithOrder(this.samplingResults, sampleResult);
        }
      }
      Logger.i("Total number of sampling result is " + this.samplingResults.size());
      
    } catch (OutOfMemoryError e) {
      throw new MeasurementError("Detect out of memory during Uplink task.");
    } catch (IOException e) {
      throw new MeasurementError("Error to send/receive data to " +
                                ((TCPThroughputDesc)measurementDesc).target);
    } finally {
      iStream.close();
      oStream.close();
      tcpSocket.close();
      Logger.i("Close uplink socket");
    }
  }

  /**
   * Downlink measurement task
   */
  private void downlink() throws MeasurementError, IOException {
    Logger.i("Start downlink task on " + ((TCPThroughputDesc)measurementDesc).target);
    Socket tcpSocket = null;
    InputStream iStream = null;
    try {
      tcpSocket = new Socket();
      buildUpSocket(tcpSocket, ((TCPThroughputDesc)measurementDesc).target,
                   ((TCPThroughputDesc)measurementDesc).port_downlink);
      iStream = tcpSocket.getInputStream();
    } catch (IOException i) {
      Logger.e("Downlink socket opening error" + i.getCause().toString());
      throw new MeasurementError("Error to open downlink socket at " +
                                ((TCPThroughputDesc)measurementDesc).target +
                                " with port " + 
                                ((TCPThroughputDesc)measurementDesc).port_downlink);
    }
    try {
      int read_bytes = 0;
      int progUpdateCount = 0;
      byte[] buffer = new byte[this.BUFFER_SIZE];
      long totalDuration = (((TCPThroughputDesc)measurementDesc).duration_period_ms + 
                           ((TCPThroughputDesc)measurementDesc).slow_start_period_ms);
      do {
        read_bytes = iStream.read(buffer, 0, buffer.length);
        updateSize(read_bytes);

        this.totalRevSize += read_bytes;
        if (this.DATA_LIMIT_ON &&
            this.totalRevSize >= 
            ((TCPThroughputDesc)measurementDesc).data_limit_bytes_down) {
          Logger.i("Detect downlink data limitation exceed with " +
                  ((TCPThroughputDesc)measurementDesc).data_limit_bytes_down + " bytes");
          this.DATA_LIMIT_EXCEEDED = true;
          break;
        }

        this.progress = (int)(this.MAXPROGRESS*(System.currentTimeMillis() - 
                        this.taskStartTime) / totalDuration);
        this.progress = Math.min(this.progress, this.MAXPROGRESS);
        // propagate every quarter
        if (this.progress >= (progUpdateCount+1)*25) {
          broadcastProgressForUser(this.progress);
          progUpdateCount++;
        }
      } while (read_bytes >= 0);

      // convert milliseconds to seconds
      this.taskDuration = (System.currentTimeMillis() - 
                          (double) this.taskStartTime) / 1000.0;
      Logger.i("Total download data is " + (double)this.totalRevSize/(1024*1024) + " MB");
      Logger.i("Total number of sampling result is " + this.samplingResults.size());
      
    } catch (OutOfMemoryError e) {
      throw new MeasurementError("Detect out of memory at Downlink task.");
    } catch  (IOException e) {
      throw new MeasurementError("Error to receive data from " +
                                ((TCPThroughputDesc)measurementDesc).target);
    } finally {
      iStream.close();
      tcpSocket.close();
      Logger.i("Close downlink socket");
    }
  }
  
  /*****************************************************************
   * Helper functions
   *****************************************************************
   * update the total received packet size
   * @param time period increment
   */
  private void updateSize(int delta) {
    double gtime = System.currentTimeMillis() - this.taskStartTime;
    //ignore slow start
    if (gtime < ((TCPThroughputDesc)measurementDesc).slow_start_period_ms)
      return;
    if (this.startSampleTime == 0) {
      this.startSampleTime = System.currentTimeMillis();
      this.accumulativeSize = 0;
    }
    this.accumulativeSize += delta;
    double time = System.currentTimeMillis() - this.startSampleTime;
    if (time < ((TCPThroughputDesc)measurementDesc).sample_period_ms) {
      return;
    } else {
      double throughput = (double)this.accumulativeSize * 8.0 / time;
      this.samplingResults = this.insertWithOrder(this.samplingResults, throughput);
      this.accumulativeSize = 0;
      this.startSampleTime = System.currentTimeMillis();
    }  
  }
  
  private void buildUpSocket(Socket tcpSocket, String hostname, int portNum)
          throws IOException {
    TCPThroughputDesc desc = (TCPThroughputDesc) measurementDesc;
    SocketAddress remoteAddr = new InetSocketAddress(hostname, portNum);
    tcpSocket.connect(remoteAddr, desc.tcp_timeout_ms);
    tcpSocket.setSoTimeout(desc.tcp_timeout_ms);
    tcpSocket.setTcpNoDelay(true);
  }
  
  private void genRandomByteArray(byte[] byteArray) {
    for (int i = 0; i < byteArray.length; i++) {
      byteArray[i] = (byte)('a' + randStr.nextInt(26));
    }
  }
  
  // insert element with ascending order, i.e. insertion sort
  private ArrayList<Double> insertWithOrder(ArrayList<Double> array, double item) {
    int i;
    for (i = 0; i < array.size(); i++ ) {
      if (item < array.get(i)) {
        break;
      } 
    }
    array.add(i,item);
    return array;
  }
}
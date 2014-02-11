/*
 * Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.mobiperf.measurements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.util.StringBuilderPrinter;

import com.mobiperf.Checkin;
import com.mobiperf.Config;
import com.mobiperf.Logger;
import com.mobiperf.MeasurementDesc;
import com.mobiperf.MeasurementError;
import com.mobiperf.MeasurementResult;
import com.mobiperf.MeasurementTask;
import com.mobiperf.RRCTrafficControl;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;


/**
 * This class measures the round trip times of packets as you vary the packet timings between them,
 * with the purpose of inferring RRC state transitions.
 * 
 * See "Characterizing Radio Resource Allocation for 3G Networks" by Feng et. al, IMC 2010 for a
 * full explanation of the methodology and goals.
 * 
 * TODO (sanae): Pause the RRC tasks when a checkin is performed, rather than pausing the checkin
 * while the RRC task is performed. 
 * 
 * @author sanae@umich.edu (Sanae Rosen)
 * 
 */
public class RRCTask extends MeasurementTask {
  // Type name for internal use
  public static final String TYPE = "rrc";
  // Human readable name for the task
  public static final String DESCRIPTOR = "rrc";
  public static String TAG = "MobiPerf_RRC_INFERENCE";
  private boolean stop = false;
  private Context context;
  
  // Track data consumption for this task to avoid exceeding user's limit
  public static long data_consumed = 0;

  /**
   * Stores parameters for the RRC inference task
   * @author sanae@umich.edu (Sanae Rosen)
   *
   */
  public static class RRCDesc extends MeasurementDesc {
    private static String HOST = "www.google.com";

    // Default echo server name and port to measure the RTT to infer RRC state
    private static int PORT = 50000;
    private static String ECHO_HOST = "ep2.eecs.umich.edu";
    // Perform RTT measurements every GRANULARITY ms
    public int GRANULARITY = 500;
    // MIN / MAX is the echo packet size
    int MIN = 0;
    int MAX = 1024;
    // Default total number of measurements
    int size = 31;
    // Echo server / port, and target to perform the upper-layer tasks
    public String echoHost = ECHO_HOST;
    public String target = HOST;
    int port = PORT;

    long testId; // unique value for this set of tests

    // Default threshold to repeat each RTT measurement because of background traffic
    int GIVEUP_THRESHHOLD = 15;

    // server controled variable
    boolean DNS = true;
    boolean TCP = true;
    boolean HTTP = true;
    boolean RRC = true;
    boolean SIZES = true;

    // Whether RRC result is visible to users
    public boolean RESULT_VISIBILITY = false;

    /*
     * For the upper-layer tests, a series of tests are made for different inter-packet intervals,
     * in order. "Times" indicates the inter-packet intervals, the other fields store the results.
     * All must be the same size.
     */
    Integer[] times; // The times where the above tests were made, in units of GRANULARITY.
    int sizeGranularity = 200; // the spacing between sizes to test
    int[] httpTest; // The results of the HTTP test performed at each time
    int[] dnsTest; // likewise, for the DNS test
    int[] tcpTest; // likewise, for the TCP test

    // Whether or not to run the upper layer tests, i.e. the HTTP, TCP and DNS tests.
    // Disabling this flag will disable all upper layer tests.
    private boolean runUpperLayerTests = false;

    /*
     * Default times between packets for which the upper layer tests are performed. Later, these
     * times will be replaced by times from the model. These times are GRANULARITY milliseconds: if
     * GRANULARITY is 500, then a default time of 6 means measurements are taken 500 ms apart.
     */
    Integer[] defaultTimesULTasks = new Integer[] {0, 2, 4, 8, 12, 16, 22};


    public RRCDesc(String key, Date startTime, Date endTime,
        double intervalSec, long count, long priority,
        Map<String, String> params) {
      super(RRCTask.TYPE, key, startTime, endTime, intervalSec, count,
          priority, params);
      initializeParams(params);
    }

    public MeasurementResult getResults(MeasurementResult result) {
      if (HTTP) result.addResult("http", httpTest);
      if (TCP) result.addResult("tcp", tcpTest);
      if (DNS) result.addResult("dns", dnsTest);
      result.addResult("times", times);
      return result;
    }

    public void displayResults(StringBuilderPrinter printer) {
      String DEL = "\t", toprint = DEL + DEL;
      for (int i = 1; i <= times.length; i++) {
        toprint += DEL + " | state" + i;
      }
      toprint += " |";
      int oneLineLen = toprint.length();
      toprint += "\n";
      // seperator
      for (int i = 0; i < oneLineLen; i++) {
        toprint += "-";
      }
      toprint += "\n";
      if (HTTP) {
        toprint += "HTTP (ms)" + DEL;
        for (int i = 0; i < httpTest.length; i++) {
          toprint += DEL + " | " + Integer.toString(httpTest[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }

      if (DNS) {
        toprint += "DNS (ms)" + DEL;
        for (int i = 0; i < dnsTest.length; i++) {
          toprint += DEL + " | " + Integer.toString(dnsTest[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }

      if (TCP) {
        toprint += "TCP (ms)" + DEL;
        for (int i = 0; i < tcpTest.length; i++) {
          toprint += DEL + " | " + Integer.toString(tcpTest[i]);
        }
        toprint += " |\n";
        for (int i = 0; i < oneLineLen; i++) {
          toprint += "-";
        }
        toprint += "\n";
      }

      toprint += "Timers (s)";
      for (int i = 0; i < times.length; i++) {
        double curTime = (double) times[i] * (double) GRANULARITY / 1000.0;
        toprint += DEL + " | " + String.format("%.2f", curTime);
      }
      toprint += " |\n";
      printer.println(toprint);
    }

    @Override
    public String getType() {
      return RRCTask.TYPE;
    }

    /**
     * Given the parameters fetched from the server, sets up the parameters as needed for the upper
     * layer tests, i.e. the application layer tests.
     */
    @Override
    protected void initializeParams(Map<String, String> params) {

      // In this case, we fall back to the default values defined above.
      if (params == null) {
        return;
      }

      // The parameters for the echo server
      this.echoHost = params.get("echo_host");
      this.target = params.get("target");
      if (this.echoHost == null) {
        this.echoHost = ECHO_HOST;
      }
      if (this.target == null) {
        this.target = HOST;
      }
      Logger.d("param: echo_host " + this.echoHost);
      Logger.d("param: target " + this.target);

      try {
        String val = null;
        // Size of the small packet
        if ((val = params.get("min")) != null && val.length() > 0
            && Integer.parseInt(val) > 0) {
          this.MIN = Integer.parseInt(val);
        }
        Logger.d("param: Min " + this.MIN);
        // Size of the large packet
        if ((val = params.get("max")) != null && val.length() > 0
            && Integer.parseInt(val) > 0) {
          this.MAX = Integer.parseInt(val);
        }
        Logger.d("param: MAX " + this.MAX);
        // Echo server port
        if ((val = params.get("port")) != null && val.length() > 0
            && Integer.parseInt(val) > 0) {
          this.port = Integer.parseInt(val);
        }
        Logger.d("param: port " + this.port);
        // Number of tests to run from the RRC test
        if ((val = params.get("size")) != null && val.length() > 0
            && Integer.parseInt(val) > 0) {
          this.size = Integer.parseInt(val);
        }
        Logger.d("param: size " + this.size);
        // When testing size dependence, increase the
        if ((val = params.get("size_granularity")) != null && val.length() > 0
            && Integer.parseInt(val) > 0) {
          this.sizeGranularity = Integer.parseInt(val);
        }
        Logger.d("param: size_granularity " + this.sizeGranularity);
        // Whether or not to run the DNS test
        if ((val = params.get("dns")) != null && val.length() > 0) {
          this.DNS = Boolean.parseBoolean(val);
        }
        Logger.d("param: DNS " + this.DNS);
        // Whether or not to run the HTTP test
        if ((val = params.get("http")) != null && val.length() > 0) {
          this.HTTP = Boolean.parseBoolean(val);
        }
        Logger.d("param: HTTP " + this.HTTP);
        // Whether or not to run the TCP test
        if ((val = params.get("tcp")) != null && val.length() > 0) {
          this.TCP = Boolean.parseBoolean(val);
        }
        Logger.d(params.get("rrc"));
        Logger.d("param: TCP " + this.TCP);
        // Whether or not to run the RRC inference task
        if ((val = params.get("rrc")) != null && val.length() > 0) {
          this.RRC = Boolean.parseBoolean(val);
        }
        Logger.d("param: RRC " + this.RRC);
        if ((val = params.get("measure_sizes")) != null && val.length() > 0) {
          this.SIZES = Boolean.parseBoolean(val);
        }
        Logger.d("param: SIZES " + this.SIZES);
        // Whether the RRC result is visible to users
        if ((val = params.get("result_visibility")) != null && val.length() > 0) {
          this.RESULT_VISIBILITY = Boolean.parseBoolean(val);
        }
        Logger.d("param: visibility " + this.RESULT_VISIBILITY);
        // How many times to retry a test when interrupted by background traffic
        if ((val = params.get("giveup_threshhold")) != null && val.length() > 0
            && Integer.parseInt(val) > 0) {
          this.GIVEUP_THRESHHOLD = Integer.parseInt(val);
        }
        Logger.d("param: GIVEUP_THRESHHOLD " + this.GIVEUP_THRESHHOLD);

        // Default assumed timers for the upper layer tests (HTTP, DNS, TCP),
        // in units of GRANULARITY. These are set via a comma-separated list
        // of numbers.
        if ((val = params.get("default_extra_test_timers")) != null
            && val.length() > 0) {
          String[] timesString = val.split("\\s*,\\s*");
          List<String> stringList =
              new ArrayList<String>(Arrays.asList(timesString));
          List<Integer> intList = new ArrayList<Integer>();
          Iterator<String> iterator = stringList.iterator();
          while (iterator.hasNext()) {
            intList.add(Integer.parseInt(iterator.next()));
          }
          times = (Integer[]) intList.toArray(new Integer[intList.size()]);

        }
        if (times == null) {
          times = defaultTimesULTasks;
        }
      } catch (NumberFormatException e) {
        throw new InvalidParameterException(
            " RRCTask cannot be created due to invalid params");
      }

      if (size == 0) {
        // 31 tests, by default. From 0s to 15s inclusive, in half-second intervals.
        size = 31;
      }
    }

    /**
     * For the arrays holding the results for the upper layer tests, we need to initialize them to
     * be the same size as the number of tests we run. -1 means uninitialized.
     * 
     * @param size Number of results to store
     */
    public void initializeExtraTaskResults(int size) {
      httpTest = new int[size];
      dnsTest = new int[size];
      tcpTest = new int[size];
      for (int i = 0; i < size; i++) {
        httpTest[i] = -1;
        dnsTest[i] = -1;
        tcpTest[i] = -1;
      }
      runUpperLayerTests = true;
    }

    /**
     * Given an interpacket interval and a round trip time from an HTTP test, store that value.
     * 
     * @param index Index into measurement result array, corresponding to an interpacket interval
     * @param rtt Time for HTTP test to complete, in milliseconds
     * @throws MeasurementError
     */
    public void setHttp(int index, int rtt) throws MeasurementError {
      if (!runUpperLayerTests) {
        throw new MeasurementError("Data class not initialized");
      }
      httpTest[index] = rtt;
    }

    /**
     * Given an interpacket interval and a round trip time from a TCP test, store that value.
     * 
     * @param index Index into measurement result array, corresponding to an interpacket interval
     * @param rtt Time for the TCP test to complete, in milliseconds
     * @throws MeasurementError
     */
    public void setTcp(int index, int rtt) throws MeasurementError {
      if (!runUpperLayerTests) {
        throw new MeasurementError("Data class not initialized");
      }
      tcpTest[index] = rtt;
    }

    /**
     * Given an interpacket interval and a round trip time from a DNS test, store that value.
     * 
     * @param index Index into measurement result array, corresponding to an interpacket interval
     * @param rtt Time for the DNS test to complete, in milliseconds
     * @throws MeasurementError
     */
    public void setDns(int index, int rtt) throws MeasurementError {
      if (!runUpperLayerTests) {
        throw new MeasurementError("Data class not initialized");
      }
      dnsTest[index] = rtt;
    }

  }

  /**
   * Stores data from the tests on the effect of packet size on RRC state related delays
   * 
   * @author sanae@umich.edu (Sanae Rosen)   
   */
  public static class RrcSizeTestData {
    int time; // Interval between packets
    int size; // Size of packets sent
    long result; // Round trip time, in milliseconds
    long testId; // A unique id associated with a set of measurements

    /**
     * 
     * @param time The inter-packet interval
     * @param size The packet size, in bytes
     * @param result The round trip time for that packet size and inter-packet interval
     * @param testId The unique id for this set of tests
     */
    public RrcSizeTestData(int time, int size, long result, long testId) {
      this.time = time;
      this.size = size;
      this.result = result;
      this.testId = testId;
    }

    public JSONObject toJSON(String networktype, String phoneId)
        throws JSONException {
      JSONObject entry = new JSONObject();
      entry.put("network_type", networktype);
      entry.put("phone_id", phoneId);
      entry.put("test_id", testId);
      entry.put("time_delay", time);
      entry.put("size", size);
      entry.put("result", result);

      return entry;
    }

  }

  /**
   * Store the results of our RRC test.
   * 
   * Data is stored as a list of results indexed by the test number. Tests are performed in order
   * with increasing inter-packet intervals and results and data about the tests are stored here.
   * 
   * @author sanae@umich.edu (Sanae Rosen)
   * 
   */
  public static class RRCTestData {

    // Round-trip times, in ms
    int[] rttsSmall;
    int[] rttsLarge;
    // Packets lost for each test
    int[] packetsLostSmall;
    int[] packetsLostLarge;
    // Signal strengths at time of each test
    int[] signalStrengthSmall;
    int[] signalStrengthLarge;
    // Error Counts from each test
    int[] errorCountSmall;
    int[] errorCountLarge;

    ArrayList<RrcSizeTestData> packetSizes;

    // Unique incrementing value that identifies this set of tests.
    long testId;

    /**
     * @param size Number of tests to run (each corresponding to an interpacket interval, 
     * increasing in increments of 500 ms)
     * @param testId Unique ID for this set ot tests
     */
    public RRCTestData(int size, long testId) {
      size = size + 1;


      rttsSmall = new int[size];
      rttsLarge = new int[size];
      packetsLostSmall = new int[size];
      packetsLostLarge = new int[size];
      signalStrengthSmall = new int[size];
      signalStrengthLarge = new int[size];
      errorCountLarge = new int[size];
      errorCountSmall = new int[size];
      
      this.testId = testId;

      packetSizes = new ArrayList<RrcSizeTestData>();

      // Set default values
      for (int i = 0; i < rttsSmall.length; i++) {
        // 7000 is the cutoff for timeouts.
        // This makes the model-building script treat no data and timeouts the same.
        rttsSmall[i] = 7000;
        rttsLarge[i] = 7000;
        packetsLostSmall[i] = -1;
        packetsLostLarge[i] = -1;
        signalStrengthSmall[i] = -1;
        signalStrengthLarge[i] = -1;
        errorCountSmall[i] = -1;
        errorCountLarge[i] = -1;
      }
    }

    public long testId() {
      return testId;
    }

    public String[] toJSON(String networktype, String phoneId) {
      String[] returnval = new String[rttsSmall.length];
      try {
        for (int i = 0; i < rttsSmall.length; i++) {
          JSONObject subtest = new JSONObject();
          subtest.put("rtt_low", rttsSmall[i]);
          subtest.put("rtt_high", rttsLarge[i]);
          subtest.put("lost_low", packetsLostSmall[i]);
          subtest.put("lost_high", packetsLostLarge[i]);
          subtest.put("signal_low", signalStrengthSmall[i]);
          subtest.put("signal_high", signalStrengthLarge[i]);
          subtest.put("error_low", errorCountSmall[i]);
          subtest.put("error_high", errorCountLarge[i]);
          subtest.put("network_type", networktype);
          subtest.put("time_delay", i);
          subtest.put("test_id", testId);
          subtest.put("phone_id", phoneId);
          returnval[i] = subtest.toString();
          Logger.w("Test ID for rrc inference test was " + this.testId);
        }
      } catch (JSONException e) {
        Logger.e("Error converting RRC data to JSON");
      }
      return returnval;
    }

    /**
     * Erase all data corresponding to a set of results with a particular interpacket interval
     * @param index Index into the array of results.
     */
    public void deleteItem(int index) {
      rttsSmall[index] = -1;
      rttsLarge[index] = -1;
      packetsLostSmall[index] = -1;
      packetsLostLarge[index] = -1;
      signalStrengthSmall[index] = -1;
      signalStrengthLarge[index] = -1;
      errorCountSmall[index] = -1;
      errorCountLarge[index] = -1;
    }

    /**
     * Save the data resulting from a given test.
     * 
     * @param index Index into the array of results.
     * @param rttMax Round trip time for large packets (generally 1KB)
     * @param rttMin Round time time for small packets (generally empty)
     * @param numPacketsLostMax Number of packets lost for the set of large packets, out of 10
     * @param numPacketsLostMin Likewise, for the small packets
     * @param errorHigh Error count for the set of large packets
     * @param errorLow Likewise, for the small packets
     * @param signalHigh The signal strength when sending the large packets
     * @param signalLow Likewise, for the small packets
     */
    public void updateAll(int index, int rttMax, int rttMin,
        int numPacketsLostMax, int numPacketsLostMin, int errorHigh,
        int errorLow, int signalHigh, int signalLow) {
      this.rttsLarge[index] = (int) rttMax;
      this.rttsSmall[index] = (int) rttMin;
      this.packetsLostLarge[index] = numPacketsLostMax;
      this.packetsLostSmall[index] = numPacketsLostMin;
      this.errorCountLarge[index] = errorHigh;
      this.errorCountSmall[index] = errorLow;
      this.signalStrengthLarge[index] = signalHigh;
      this.signalStrengthSmall[index] = signalLow;
    }

    public void setRrcSizeTestData(int index, int size, long result, long testId)
        throws MeasurementError {
      packetSizes.add(new RrcSizeTestData(index, size, result, testId));
    }

    public String[] sizeDataToJSON(String networkType, String phoneId) {
      String[] returnval = new String[packetSizes.size()];
      try {
        for (int i = 0; i < packetSizes.size(); i++) {
          returnval[i] =
              packetSizes.get(i).toJSON(networkType, phoneId).toString();
        }
      } catch (JSONException e) {
        Logger.e("Error converting RRC data to JSON");
      }
      return returnval;
    }
  }

  /**
   * Class for tracking if there has been interfering traffic.
   * 
   * We need to know how many packets are expected. We can then use the global packet counters to
   * see if more packets are sent than expected.
   * 
   * @author sanae@umich.edu (Sanae Rosen)
   * 
   */
  public static class PacketMonitor {
    private long[] packetsFirst;
    private long[] packetsLast;
    boolean bySize = false;

    /**
     * Initialize immediately before use. Values are time-sensitive.
     */
    PacketMonitor() {
      readCurrentPacketValues();
    }

    void setBySize() {
      bySize = true;
    }

    void readCurrentPacketValues() {
      packetsFirst = getPacketsSent();
    }

    /**
     * Call this to determine if packets have been sent since initializing.
     * 
     * @param expectedRcv Number of packets expected to be received by the device since 
     * initialization
     * @param expectedSent Number of packets expected to be sent by the device since 
     * initialization
     * @return Whether or not there is interfering traffic
     */
    boolean isTrafficInterfering(int expectedRcv, int expectedSent) {
      packetsLast = getPacketsSent();

      long rcvPackets = (packetsLast[0] - packetsFirst[0]);
      long sentPackets = (packetsLast[1] - packetsFirst[1]);
      if (rcvPackets <= expectedRcv && sentPackets <= expectedSent) {
        Logger.d("No competing traffic, continue");
        return false;
      }
      Logger.d("Competing traffic, retry");

      return true;
    }

    /**
     * Determine how many packets, so far, have been sent (the contents of /proc/net/dev/). This is
     * a global value. We use this to determine if any other app anywhere on the phone may have sent
     * interfering traffic that might have changed the RRC state without our knowledge.
     * 
     * @return Two values: number of bytes or packets received at index 0, followed by the number 
     * sent at index 1.  
     */
    public long[] getPacketsSent() {
      long[] retval = {-1, -1};
      if (bySize) {
        retval[0] = TrafficStats.getMobileRxBytes();
        retval[1] = TrafficStats.getMobileTxBytes();

      } else {
        retval[0] = TrafficStats.getMobileRxPackets();
        retval[1] = TrafficStats.getMobileTxPackets();
      }

      return retval;
    }
    
    /**
     * The total traffic sent and received since getPacketsSent was run.
     * 
     * @return The total packets (or data) sent and received, as a sum
     */
    public long getPacketsSentDiff() {
      packetsLast = getPacketsSent();
      long rcvPackets = (packetsLast[0] - packetsFirst[0]);
      long sentPackets = (packetsLast[1] - packetsFirst[1]);
      return rcvPackets + sentPackets;
    }
  }


  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return RRCDesc.class;
  }

  public RRCTask(MeasurementDesc desc, Context parent) {
    super(new RRCDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
        desc.count, desc.priority, desc.parameters), parent);
    context = parent;
  }

  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    RRCDesc newDesc =
        new RRCDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
            desc.count, desc.priority, desc.parameters);
    return new RRCTask(newDesc, parent);
  }

  @Override
  public MeasurementResult call() throws MeasurementError {
    RRCDesc desc = runInferenceTests();
    return constructResultStandard(desc);
  }

  @Override
  public String getDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  public String getType() {
    return RRCTask.TYPE;
  }

  @Override
  public void stop() {
    stop = true;
  }

  /**
   * Helper function to construct MeasurementResults to submit to the server
   * 
   * @param desc The data collected for the RRC test to be converted into something understandable 
   * by the server. 
   * @return A data structure that can be sent to the server.  Contains only the results of the 
   * TCP, DNS and HTTP tests: the others are sent via a separate mechanism as they are stored 
   * separately. 
   */
  private MeasurementResult constructResultStandard(RRCDesc desc) {
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
    boolean success = true;
    MeasurementResult result =
        new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
            phoneUtils.getDeviceProperty(), RRCTask.TYPE,
            System.currentTimeMillis() * 1000, success, this.measurementDesc);

    if (desc.runUpperLayerTests) {
      result = desc.getResults(result);
    }

    Logger.i(MeasurementJsonConvertor.toJsonString(result));
    return result;
  }

  /**
   * The core RRC inference functionality is in this function. The steps involved can be summarized
   * as follows:
   * <ol> 
   * <li> Fetch the last model generated by the server, if it exists.</li> 
   * <li> Check we are on a cellular network, otherwise abort. </li>
   * <li> Inform all other tasks that they should delay network traffic until later. </li>
   * <li> For every upper layer test, if upper layer tests and that test specifically are enabled,
   * run that test.</li> 
   * </ol> 
   * @return A data structure containing all the results and metadata surrounding the RRC inference tests 
   * performed.
   * @throws MeasurementError
   */
  private RRCDesc runInferenceTests() throws MeasurementError {
    data_consumed = 0;

    Checkin checkin = new Checkin(context);

    // Fetch the existing model from the server, if it exists
    RRCDesc desc = (RRCDesc) measurementDesc;
    desc.testId = getTestId(context);
    Logger.w("Test ID set to " +desc.testId);
    PhoneUtils utils = PhoneUtils.getPhoneUtils();
    desc.initializeExtraTaskResults(desc.times.length);

    // Check to make sure we are on a valid (i.e. cellular) network
    if (utils.getNetwork() == "UNKNOWN" || utils.getNetwork() == "WIRELESS") {
      Logger.d("Returning: network is" + utils.getNetwork() + " rssi "
          + utils.getCurrentRssi());
      return desc;
    }

    try {
      /*
       * Suspend all other tasks performed by the app as they can interfere. Although we have a
       * built-in check where we abort if traffic in the background interferes, in the past people
       * have scheduled other tests to be every 5 minutes, which can cause the RRC task to never
       * successfully complete without having to abort.
       */
      RRCTrafficControl.PauseTraffic();

      RRCTestData data = new RRCTestData(desc.size, desc.testId);

      // If the RRC task is enabled
      if (desc.RRC) {
        // Set up the connection to the echo server
        Logger.d("Active inference: about to begin");
        Logger.d(desc.echoHost + ":" + desc.port);
        InetAddress serverAddr = InetAddress.getByName(desc.echoHost);

        // Perform the RRC timer and latency inference task
        Logger.d("Demotion inference: about to begin");
        desc = inferDemotion(serverAddr, desc, data, utils);

        Logger.d("About to save data");
        this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 40);
        try {
          Logger.w("RRC: update the model on the GAE datastore");
          checkin.uploadRrcInferenceData(data);
          Logger.d("Saving data complete");
        } catch (IOException e) {
          e.printStackTrace();
          Logger.e("Data not saved: " + e.getMessage());
        }
      }

      // Check if the upper layer tasks are enabled
      if (desc.runUpperLayerTests) {
        if (desc.DNS) {
          Logger.w("Start DNS task");
          // Test the dependence of DNS latency on the RRC state, using
          // the previously constructed model if available.
          runDnsTest(desc.times, desc);
        }
        this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 60);
        if (desc.TCP) {
          Logger.w("Start TCP task");
          // Test the dependence of TCP latency on the RRC state.
          runTCPHandshakeTest(desc.times, desc);
        }
        this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 80);
        if (desc.HTTP) {
          Logger.w("Start HTTP task");
          // Test the dependence of HTTP latency on the RRC state.
          runHTTPTest(desc.times, desc);
        }

        if (desc.SIZES) {
          Logger.w("Start size dependence task");
          runSizeThresholdTest(desc.times, desc, data, desc.testId);
          checkin.uploadRrcInferenceSizeData(data);
        }
      }

      this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, 100);
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (SocketException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      RRCTrafficControl.UnPauseTraffic();
    }

    return desc;
  }

  /**
   * Determines the packet size dependence of the RRC task.
   * 
   * Given a specified list of inter-packet intervals, perform the RRC inference measurement for
   * packets of sizes incremented by the size granularity specified.
   * 
   * @param times  Inter-packet intervals to test
   * @param desc Parameters for the RRC inference task
   * @param data Stores results of the RRC inference task
   * @param testId A unique ID identifying this set of tests
   */
  private void runSizeThresholdTest(final Integer[] times, RRCDesc desc,
      RRCTestData data, long testId) {

    InetAddress serverAddr;
    try {
      serverAddr = InetAddress.getByName(desc.echoHost);
    } catch (UnknownHostException e) {
      Logger.e("Invalid or unreachable echo host. Test aborted.");
      e.printStackTrace();
      return;
    }
    for (int i = 0; i < times.length; i++) {
      for (int j = desc.sizeGranularity; j <= 1024; j += desc.sizeGranularity) {
        try {
          long result =
              inferDemotionPacketSize(serverAddr, times[i], desc, j);
          data.setRrcSizeTestData(times[i], j, result, testId);
        } catch (IOException e) {
          e.printStackTrace();
        } catch (InterruptedException e) {
          e.printStackTrace();
        } catch (MeasurementError e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Due to problems with how we detect interfering traffic, this test does not always work and
   * results should be treated with caution. I am keeping this test around, though, as we can still 
   * get data on how much HTTP handshakes vary in how long they take.
   * 
   * The "times" are the inter-packet intervals at which to run the test. Ideally these should be
   * based on the model constructed by the server, a default assumed value is used in their absence.
   * 
   * Based on the time it takes to load a response from the page.
   * 
   * This test is not currently as accurate as the other tests, for reasons described below, and has
   * thus been disabled.
   * 
   * @param times List of inter-packet intervals, in half-second increments, at which to run the 
   * tests
   * @param desc Stores parameters for the RRC inference tests in general
   */
  private void runHTTPTest(final Integer[] times, RRCDesc desc) {
    /*
     * Length of time it takes to request and read in a page.
     */

    Logger.d("Active inference HTTP test: about to begin");
    if (times.length != desc.httpTest.length) {
      desc.httpTest = new int[times.length];
    }
    long startTime = 0;
    long endTime = 0;

    
    try {
      for (int i = 0; i < times.length; i++) {
        // We try until we reach a threshhold or until there is no
        // competing traffic.
        for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
          // Sometimes the network can change in the middle of a test
          checkIfWifi();
          if (stop) {
            return;
          }

          /*
           * We keep track of the packets sent at the beginning and end of the test so we can detect
           * if there is competing traffic anywhere on the phone.
           */
          PacketMonitor packetMonitor;
          
          /*
           * We also keep track of the data consumed in order to remain within
           * the data limit
           */
          PacketMonitor datamonitor = new PacketMonitor();
          datamonitor.setBySize();
          datamonitor.readCurrentPacketValues();
          

          // Initiate the desired RRC state by sending a large enough packet
          // to go to DCH and waiting for the specified amount of time
          try {              
            // Wait for 1 second. Give time for any extraneous data sending to complete
            waitTime(1, false); 
            InetAddress serverAddr;
            serverAddr = InetAddress.getByName(desc.echoHost);
            sendPacket(serverAddr, desc.MAX, desc);
            packetMonitor = new PacketMonitor();

            waitTime(times[i] * desc.GRANULARITY, true);

          } catch (InterruptedException e1) {
            e1.printStackTrace();
            continue;
          } catch (UnknownHostException e) {
            e.printStackTrace();
            continue;
          } catch (IOException e) {
            e.printStackTrace();
            continue;
          }
          startTime = System.currentTimeMillis();
          
          // Somewhat approximte: we can pick up packets sent by our request.
          // Our request seems to never send more than 24 packets when there is no interference.
          boolean success = !packetMonitor.isTrafficInterfering(3, 70);
          HttpClient client = new DefaultHttpClient();
          HttpGet request = new HttpGet();

          request.setURI(new URI("http://" + desc.target+"?dummy="+i + "" +j));

          HttpResponse response = client.execute(request);
          endTime = System.currentTimeMillis();

          BufferedReader in = null;
          in =
              new BufferedReader(new InputStreamReader(response.getEntity()
                  .getContent()));
          StringBuffer sb = new StringBuffer("");
          String line = "";

          while ((line = in.readLine()) != null) {
            sb.append(line + "\n");
          }
          in.close();

          // This may overestimate the data consumed, but there's no good way
          // to tell what was us and what was another app
          data_consumed += datamonitor.getPacketsSentDiff();

          if (success) {
            break;
          } 
          startTime = 0;
          endTime = 0;

        }

        long rtt = endTime - startTime;
        try {
          desc.setHttp(i, (int) rtt);
        } catch (MeasurementError e) {
          e.printStackTrace();
        }
        Logger.d("Time for Http" + rtt);
      }
    } catch (ClientProtocolException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }

  /**
   * The "times" are the inter-packet intervals at which to run the test. Ideally these should be
   * based on the model constructed by the server, a default assumed value is used in their absence.
   * 
   * <ol>
   * <li> Send a packet to initiate the RRC state desired. </li>
   * <li>Create a randomly generated host name (to ensure that the host name is not cached). I 
   * found on some devices that even when you clear the cache manually, the data remains in 
   * the cache.</li>
   * <li> Time how long it took to look it up.</li> 
   * <li> Count the total packets sent, globally on the phone. If more packets were sent than </li>
   * expected,  abort and try again. </li> 
   * <li> Otherwise, save the data for that test and move to the next inter-packet interval.</li>
   * </ol>
   * 
   * Test is similar to the approach taken in DnsLookUpTask.java.
   * 
   * @param times List of inter-packet intervals, in half-second increments, at which to run the 
   * test
   * @param desc Stores parameters for the RRC inference tests in general
   * @throws MeasurementError
   */

  public void runDnsTest(final Integer[] times, RRCDesc desc)
      throws MeasurementError {
    Logger.d("Active inference DNS test: about to begin");
    if (times.length != desc.dnsTest.length) {
      desc.dnsTest = new int[times.length];
    }
    long dataConsumedThisTask = 0;

    long startTime = 0;
    long endTime = 0;

    // For each inter-packet interval...
    for (int i = 0; i < times.length; i++) {
      // On a failure, try again until a threshold is reached.
      for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {

        // Sometimes the network can change in the middle of a test
        checkIfWifi();
        if (stop) {
          return;
        }

        /*
         * We keep track of the packets sent at the beginning and end of the test so we can detect
         * if there is competing traffic anywhere on the phone.
         */

        PacketMonitor packetMonitor = new PacketMonitor();


        // Initiate the desired RRC state by sending a large enough packet
        // to go to DCH and waiting for the specified amount of time
        try {
          InetAddress serverAddr;
          serverAddr = InetAddress.getByName(desc.echoHost);
          sendPacket(serverAddr, desc.MAX, desc);
          waitTime(times[i] * desc.GRANULARITY, true);
        } catch (InterruptedException e1) {
          e1.printStackTrace();
          continue;
        } catch (UnknownHostException e) {
          e.printStackTrace();
          continue;
        } catch (IOException e) {
          e.printStackTrace();
          continue;
        }

        // Create a random URL, to avoid the caching problem
        UUID uuid = UUID.randomUUID();
        String host = uuid.toString() + ".com";
        // Start measuring the time to complete the task
        startTime = System.currentTimeMillis();
        try {
          @SuppressWarnings("unused")
          InetAddress serverAddr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
          // we do this on purpose! Since it's a fake URL the lookup will fail
        }
        dataConsumedThisTask += DnsLookupTask.AVG_DATA_USAGE_BYTE;
        // When we fail to find the URL, we stop timing
        endTime = System.currentTimeMillis();

        // Check how many packets were sent again. If the expected number
        // of packets were sent, we can finish and go to the next task.
        // Otherwise, we have to try again.
        if (!packetMonitor.isTrafficInterfering(5, 5)) {
          break;
        }

        startTime = 0;
        endTime = 0;
      }

      // If we broke out of the try-again loop, the last set of results are
      // valid and we can save them.
      long rtt = endTime - startTime;
      try {
        desc.setDns(i, (int) rtt);
      } catch (MeasurementError e) {
        e.printStackTrace();
      }
      Logger.d("Time for DNS" + rtt);
    }
    incrementData(dataConsumedThisTask);
  }

  /**
   * Time how long it takes to do a TCP 3-way handshake, starting from the induced RRC state.
   * 
   * <ol>
   * <li> Send a packet to initiate the RRC state desired. </li>
   * <li> Open a TCP connection to the echo host server. </li>
   * <li> Time how long it took to look it up. </li>
   * <li> Count the total packets sent, globally on  the phone. If more packets were sent than 
   * expected, abort and try again. </li>
   * <li> Otherwise, save the data for that test and move to the next inter-packet interval.
   * </ol>
   * 
   * @param times List of inter-packet intervals, in half-second increments, at which to run the 
   * test
   * @param desc Stores parameters for the RRC inference tests in general
   */
  public void runTCPHandshakeTest(final Integer[] times, RRCDesc desc) {
    Logger.d("Active inference TCP test: about to begin");
    if (times.length != desc.tcpTest.length) {
      desc.tcpTest = new int[times.length];
    }
    long startTime = 0;
    long endTime = 0;
    long dataConsumedThisTask = 0;

    try {
      // For each inter-packet interval...
      for (int i = 0; i < times.length; i++) {
        // On a failure, try again until a threshhold is reached.
        for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
          checkIfWifi();
          if (stop) {
            return;
          }

          PacketMonitor packetMonitor = new PacketMonitor();

          // Induce DCH then wait for specified time
          InetAddress serverAddr;
          serverAddr = InetAddress.getByName(desc.echoHost);
          sendPacket(serverAddr, desc.MAX, desc);
          waitTime(times[i] * 500, true);

          // begin test. We test the time to do a 3-way handshake only.
          startTime = System.currentTimeMillis();

          serverAddr = InetAddress.getByName(desc.target);
          // three-way handshake done when socket created
          Socket socket = new Socket(serverAddr, 80);
          endTime = System.currentTimeMillis();
          
          // Not exact, but also a smallish task...
          dataConsumedThisTask += DnsLookupTask.AVG_DATA_USAGE_BYTE;

          // Check how many packets were sent again. If the expected number
          // of packets were sent, we can finish and go to the next task.
          // Otherwise, we have to try again.
          if (!packetMonitor.isTrafficInterfering(5, 4)) {
            socket.close();
            break;
          }
          startTime = 0;
          endTime = 0;
          socket.close();
        }
        long rtt = endTime - startTime;
        try {
          desc.setTcp(i, (int) rtt);
        } catch (MeasurementError e) {
          e.printStackTrace();
        }
        Logger.d("Time for TCP" + rtt);
      }
    } catch (InterruptedException e1) {
      e1.printStackTrace();
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    incrementData(dataConsumedThisTask);
  }

  /**
   * 
   * For all time intervals specified, go through and perform the RRC inference test.
   *  
   * The way this test works, at a high level, is: 
   * <ol>
   * <li> Send a large packet to induce DCH (or the equivalent) </li>
   * <li> Wait for an amount of time, t </li>
   * <li> Send a large packet and measure the round-trip time </li>
   * <li> Wait for another amount of time, t </li>
   * <li> Send a small packet and measure the round-trip time 6. Repeat for all specified values 
   * of t. These start at 0 and increase by GRANULARITY, "size" times. </li>
   * </ol>
   * 
   * The size of "small" and "large" packets are defined in the parameters. We observe the total
   * packets sent to make sure there is no interfering traffic.
   * 
   * Packets are UDP packets.
   * 
   * From this, we can infer the timers associated with RRC states. By sending a large packet, we
   * induce the highest power state. Waiting a number of seconds afterwards allows us to demote to
   * the next state. Sending a packet and observing the RTT allows us to infer if a state promotion
   * had to take place.
   * 
   * FACH is characterized by different state promotion times for large and small packets.
   * 
   * @param serverAddr Address of the echo server
   * @param desc Stores the parameters for the RRC tests
   * @param data Stores the results of the RRC tests
   * @param utils For fetching the signal strength when the test is performed
   * @return The parameters for the RRC tests
   * @throws InterruptedException
   * @throws IOException
   */
  private RRCDesc inferDemotion(InetAddress serverAddr, RRCDesc desc,
      RRCTestData data, PhoneUtils utils) throws InterruptedException,
      IOException {
    Logger.d("Demotion basic test");

    for (int i = 0; i <= desc.size; i++) {
      this.progress =
          Math.min(Config.MAX_PROGRESS_BAR_VALUE, (int) (100 * i / (desc.size)));

      checkIfWifi();
      if (stop) {
        return desc;
      }
      inferDemotionHelper(serverAddr, i, data, desc, utils);
      Logger.d("Finished demotion test with length" + i);

      // Note that we scale from 0-90 to save some stuff for upper layer tests.
      // If we wanted to really do this properly we could scale
      // according to how long each task should take.
      this.progress =
          Math.min(Config.MAX_PROGRESS_BAR_VALUE, (int) (90 * i / desc.size));
    }
    return desc;
  }

  @Override
  public String toString() {
    RRCDesc desc = (RRCDesc) measurementDesc;
    return "[RRC]\n  Echo Server: " + desc.echoHost + "\n  Target: "
        + desc.target + "\n  Interval (sec): " + desc.intervalSec
        + "\n  Next run: " + desc.startTime;
  }

  /*********************************************************************
   * UTILITIES *
   *********************************************************************/

  /**
   * 
   * Sleep for the amount of time indicated.
   * 
   * @param timeToSleep Time for which we pause the current thread
   * @param useMs Toggles between units of milliseconds for the first parameter (true) and
   *        seconds(false).
   * @throws InterruptedException
   */
  public static void waitTime(int timeToSleep, boolean useMs)
      throws InterruptedException {
    Logger.d("Wait for n ms: " + timeToSleep);

    if (!useMs) {
      timeToSleep = timeToSleep * 1000;
    }
    Thread.sleep(timeToSleep);
  }

  /**
   * Sends a bunch of UDP packets of the size indicated and wait for the response.
   * 
   * Counts how long it takes for all the packets to return. PAckets are currently not labelled: the
   * total time is the time for the first packet to leave until the last packet arrives. AFter 7000
   * ms it is assumed packets are lost and the socket times out. In that case, the number of packets
   * lost is recorded.
   * 
   * @param serverAddr server to which to send the packets
   * @param size size of the packets
   * @param num number of packets to send
   * @param packetSize size of the packets sent
   * @param port port to send the packets to
   * @return first value: the amount of time to send all packets and get a response. second value:
   *         number of packets lost, on a timeout.
   * @throws IOException
   */
  public static long[] sendMultiPackets(InetAddress serverAddr, int size,
      int num, int packetSize, int port) throws IOException {

    long startTime = 0;
    long endTime = 0;
    byte[] buf = new byte[size];
    byte[] rcvBuf = new byte[packetSize];
    long[] retval = {-1, -1};
    long numLost = 0;
    int i = 0;
    long dataConsumedThisTask = 0;
            
    DatagramSocket socket = new DatagramSocket();
    DatagramPacket packetRcv = new DatagramPacket(rcvBuf, rcvBuf.length);
    DatagramPacket packet =
        new DatagramPacket(buf, buf.length, serverAddr, port);

    // number * (packet sent + packet received)
    dataConsumedThisTask += num * (size + packetSize);
    
    try {
      socket.setSoTimeout(7000);
      startTime = System.currentTimeMillis();
      Logger.d("Sending packet, waiting for response ");
      for (i = 0; i < num; i++) {
        socket.send(packet);
      }
      for (i = 0; i < num; i++) {
        socket.receive(packetRcv);
        if (i == 0) {

          endTime = System.currentTimeMillis();
        }
      }
    } catch (SocketTimeoutException e) {
      Logger.d("Timed out");
      numLost += (num - i);
      socket.close();
    }
    Logger.d("Sending complete: " + endTime);

    retval[0] = endTime - startTime;
    retval[1] = numLost;
    
    incrementData(dataConsumedThisTask);
    return retval;
  }
  
  /**
   * Helper function that sends a single packet and receives an empty packet back. 
   * @param serverAddr Echo server to calculate round trip
   * @param size size of packet to send in bytes
   * @param desc Holds parameters for the RRC inference task
   * @return The round trip time for the packet
   * @throws IOException
   */
  private static long sendPacket(InetAddress serverAddr, int size, RRCDesc desc)
      throws IOException {
    return sendPacket(serverAddr, size, desc.MIN, desc.port);
  }

  /** 
   * Send a single packet of the size indicated and wait for a response.
   * 
   * After 7000 ms, time out and return a value of -1 (meaning no response). Otherwise, return the
   * time from when the packet was sent to when a response was returned by the echo server.
   * 
   * @param serverAddr Echo server to calculate round trip
   * @param size size of packet to send in bytes
   * @param rcvSize size of packets sent from the echo server
   * @param port where the echo server is listening
   * @return The round trip time for the packet
   * @throws IOException
   */
  public static long sendPacket(InetAddress serverAddr, int size, int rcvSize,
      int port) throws IOException {
    long startTime = 0;
    byte[] buf = new byte[size];
    byte[] rcvBuf = new byte[rcvSize];
    long dataConsumedThisTask = 0;

    DatagramSocket socket = new DatagramSocket();
    DatagramPacket packetRcv = new DatagramPacket(rcvBuf, rcvBuf.length);
    
    dataConsumedThisTask += (size + rcvSize);


    DatagramPacket packet =
        new DatagramPacket(buf, buf.length, serverAddr, port);

    try {
      socket.setSoTimeout(7000);
      startTime = System.currentTimeMillis();
      Logger.d("Sending packet, waiting for response ");

      socket.send(packet);
      socket.receive(packetRcv);
    } catch (SocketTimeoutException e) {
      Logger.d("Timed out, trying again");
      socket.close();
      return -1;
    }
    long endTime = System.currentTimeMillis();
    Logger.d("Sending complete: " + endTime);
    incrementData(dataConsumedThisTask);
    return endTime - startTime;
  }

  /**
   * Performs a single RRC inference test to account for packet sizes.
   * 
   * Sends a packet, waits for the specified length of time, then sends a cluster of packets of 
   * the specified size.
   * 
   * @param serverAddr  Echo server to calculate round trip
   * @param wait Time to wait between packets, in milliseconds.
   * @param desc  Holds parameters for the RRC inference task
   * @param size Size, in bytes, of the packet to send.
   * @return The amount of time to send all packets and get a response.
   * @throws IOException
   * @throws InterruptedException
   */
  public static long inferDemotionPacketSize(InetAddress serverAddr, int wait,
      RRCDesc desc, int size) throws IOException,
      InterruptedException {
    long retval = -1;
    for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
      Logger.d("Active inference: determine packet size, size " + size
          + " interval " + wait);

      // Induce the highest power state
      sendPacket(serverAddr, desc.MAX, desc.MIN, desc.port);

      // WAit for the specified amount of time
      waitTime(wait * desc.GRANULARITY, true);

      // Send the specified packet size
      long[] rtts = sendMultiPackets(serverAddr, size, 1, desc.MIN, desc.port);
      long rttPacket = rtts[0];

      PacketMonitor packetMonitor = new PacketMonitor();
      if (!packetMonitor.isTrafficInterfering(3, 3)) {
        retval = rttPacket;
        break;
      }
    }
    return retval;
  }

  private long[] inferDemotionHelper(InetAddress serverAddr, int wait,
      RRCTestData data, RRCDesc desc, PhoneUtils utils) throws IOException,
      InterruptedException {
    return inferDemotionHelper(serverAddr, wait, data, desc, wait, utils);
  }
  
  /**
   * One component of the RRC inference task. 
   * 
   * <ol>
   * <li> Induce the highest-power RRC state by sending a large packet. </li>
   * <li> Wait the indicated number of seconds. </li>
   * <li> Send a series of 10 large packets at once. Measure: a) Time for all packets to be 
   * echoed back b) number of packets lost, if any c) associated signal strength d) error rate 
   * is currently not implemented. </li>
   * <li> Check if the expected number of packets were sent while performing a test. If too many
   *  packets were sent, abort. </li>
   * </ol>
   * 
   * @param serverAddr Echo server to calculate round trip
   * @param wait Time in milliseconds to pause between packets sent
   * @param data Stores the results of the RRC inference tests 
   * @param desc Stores parameters for the RRC inference tests
   * @param index Index of the current test, corresponds to the inter-packet time in intervals of 
   * half milliseconds
   * @param utils Used to retrieve the phone's RSSI at the time of collecting the data
   * @return first value: the amount of time to send all small packets and get a response. 
   * Second value: time to send all large packets and get a response.
   * @throws IOException
   * @throws InterruptedException
   */
  public static long[] inferDemotionHelper(InetAddress serverAddr, int wait,
      RRCTestData data, RRCDesc desc, int index, PhoneUtils utils)
      throws IOException, InterruptedException {
    /**
     * Once we generalize the RRC state inference problem, this is what we will use (since in
     * general, RRC state can differ between large and small packets). Gives the RTT for a large
     * packet and a small packet for a given time after inducing DCH state. Granularity currently
     * half-seconds but can easily be increased.
     * 
     * Measures packets sent before and after to make sure no extra packets were sent, and retries
     * on a failure. Also checks that there was no timeout and retries on a failure.
     */
    long rttLargePacket = -1;
    long rttSmallPacket = -1;
    int packetsLostSmall = 0;
    int packetsLostLarge = 0;

    int errorCountLarge = 0;
    int errorCountSmall = 0;
    int signalStrengthLarge = 0;
    int signalStrengthSmall = 0;

    for (int j = 0; j < desc.GIVEUP_THRESHHOLD; j++) {
      Logger.d("Active inference: about to begin helper");

      PacketMonitor packetMonitor = new PacketMonitor();

      // Induce the highest power state
      sendPacket(serverAddr, desc.MAX, desc.MIN, desc.port);

      // WAit for the specified amount of time
      waitTime(wait * desc.GRANULARITY, true);

      // Send a bunch of large packets, all at once, and take measurements on the result
      signalStrengthLarge = utils.getCurrentRssi();
      long[] retval =
          sendMultiPackets(serverAddr, desc.MAX, 10, desc.MIN, desc.port);
      packetsLostSmall = (int) retval[1];
      rttLargePacket = retval[0];

      // wait for the specified amount of time
      waitTime(wait * desc.GRANULARITY, true);

      // Send a bunch of small packets, all at once, and take measurements on the result
      signalStrengthSmall = utils.getCurrentRssi();
      retval = sendMultiPackets(serverAddr, desc.MIN, 10, desc.MIN, desc.port);
      packetsLostLarge = (int) retval[1];
      rttSmallPacket = retval[0];

      if (!packetMonitor.isTrafficInterfering(21, 21)) {
        break;
      }
      Logger.d("Try again.");
      rttLargePacket = -1;
      rttSmallPacket = -1;
      packetsLostSmall = 0;
      packetsLostLarge = 0;

      errorCountLarge = 0;
      errorCountSmall = 0;
      signalStrengthLarge = 0;
      signalStrengthSmall = 0;
    }

    Logger.d("3G demotion, lower bound: rtts are:" + rttLargePacket + " "
        + rttSmallPacket + " " + packetsLostSmall + " " + packetsLostLarge);

    long[] retval = {rttLargePacket, rttSmallPacket};
    data.updateAll(index, (int) rttLargePacket, (int) rttSmallPacket,
        packetsLostSmall, packetsLostLarge, errorCountLarge, errorCountSmall,
        signalStrengthLarge, signalStrengthSmall);

    return retval;
  }

  /**
   * Keep a global counter that labels each test with a unique, increasing integer.
   * 
   * @param context Any context instance, needed to fetch the last test id from permanent storage
   * @return The unique (for this device) test ID generated.
   */
  public static synchronized int getTestId(Context context) {
    SharedPreferences prefs =
        context.getSharedPreferences("test_ids", Context.MODE_PRIVATE);
    int testid = prefs.getInt("test_id", 0) + 1;
    SharedPreferences.Editor editor = prefs.edit();
    editor.putInt("test_id", testid);
    editor.commit();
    return testid;
  }

  /**
   * If on wifi, suspend the task until we go back to the cellular network. Use exponential back-off
   * to calculate the wait time, with a limit of 500 s. Once the limit is reached, unpause other
   * tasks. Repause traffic if we are good to resume again.
   * 
   */
  public void checkIfWifi() {
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();

    int timeToWait = 10;
    while (true) {
      if (phoneUtils.getNetwork() != PhoneUtils.NETWORK_WIFI) {
        RRCTrafficControl.PauseTraffic();
        return;
      }
      if (stop) {
        return;
      }

      if (timeToWait < 60) {
        RRCTrafficControl.UnPauseTraffic();
      }
      Logger.d("RRCTask: on Wifi, try again later: " + phoneUtils.getNetwork());
      if (timeToWait < 500) { // 500s, or a bit over 8 minutes.
        timeToWait = timeToWait * 2;
      } else {
        // if it's taking a while, stop pausing traffic
      }
      try {
        waitTime(timeToWait, false);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
  
  private synchronized static void incrementData(long data_increment) {
      data_consumed += data_increment;
  }

  /**  
   * For RRC inference, we calculate this precisely based on the number and size
   * of packets sent.  For the TCP handshake and DNS tasks, we use small, fixed 
   * values based on the average data consumption measured for those tasks.
   * 
   * For the HTTP task, we count <i>all</i> data sent during the task time towards
   * the budget.  This will tend to overestimate the data used, but due to 
   * retransmissions, etc, it is impossible to get a remotely accurate estimate 
   * otherwise, I found.
   */
  @Override
  public long getDataConsumed() {
    return data_consumed;
  }
}

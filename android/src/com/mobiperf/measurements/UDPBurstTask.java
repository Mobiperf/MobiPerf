/* Copyright 2013 RobustNet Lab, University of Michigan. All Rights Reserved.
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

import com.mobiperf.Logger;
import com.mobiperf.MeasurementDesc;
import com.mobiperf.MeasurementError;
import com.mobiperf.MeasurementResult;
import com.mobiperf.MeasurementTask;
import com.mobiperf.util.MLabNS;
import com.mobiperf.util.PhoneUtils;

import android.content.Context;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

/**
 * 
 * UDPBurstTask provides two types of measurements, Burst Up and Burst Down,
 * described next.
 * 
 * 1. UDPBurst Up: the device sends sends a burst of UDPBurstCount UDP packets
 * and waits for a response from the server that includes the number of packets
 * that the server received
 * 
 * 2. UDPBurst Down: the device sends a request to a remote server on a UDP port
 * and the server responds by sending a burst of UDPBurstCount packets. The size
 * of each packet is packetSizeByte
 */
public class UDPBurstTask extends MeasurementTask {

  public static final String TYPE = "udp_burst";
  public static final String DESCRIPTOR = "UDP Burst";

  private static final int DEFAULT_UDP_PACKET_SIZE = 100;
  private static final int DEFAULT_UDP_BURST = 16;
  private static final int DEFAULT_UDP_INTERVAL = 256;

  // Min packet size =  (int type) + (int burstCount) + (int packetNum) + (int intervalNum) +
  //                    (long timestamp) + (int packetSize) + (int seq) + (int udpInterval)
  //                 =  36
  private static final int MIN_PACKETSIZE = 36;
  
  private static final int MAX_PACKETSIZE = 500;
  private static final int DEFAULT_PORT = 31341;
  // Todo(Hongyi): choose a proper timeout period
  private static final int RCV_TIMEOUT = 16000; // in msec.

  private static final int PKT_ERROR = 1;
  private static final int PKT_RESPONSE = 2;
  private static final int PKT_DATA = 3;
  private static final int PKT_REQUEST = 4;

  private String targetIp = null;
  private Context context = null;

  private static int seq = 1;
  
  // Track data consumption for this task to avoid exceeding user's limit
  private long dataConsumed;

  /**
   * Encode UDP specific parameters, along with common parameters inherited
   * from MeasurementDesc
   * 
   */
  public static class UDPBurstDesc extends MeasurementDesc {
    // Declare static parameters specific to SampleMeasurement here

    public int packetSizeByte = UDPBurstTask.DEFAULT_UDP_PACKET_SIZE;
    public int udpBurstCount = UDPBurstTask.DEFAULT_UDP_BURST;
    public int dstPort = UDPBurstTask.DEFAULT_PORT;
    public String target = null;
    public boolean dirUp = false;
    public int udpInterval = UDPBurstTask.DEFAULT_UDP_INTERVAL;
    
    public UDPBurstDesc(String key, Date startTime, Date endTime,
        double intervalSec, long count, long priority,
        Map<String, String> params)
        throws InvalidParameterException {
      super(UDPBurstTask.TYPE, key, startTime, endTime, intervalSec,
          count, priority, params);
      initializeParams(params);
      if (this.target == null || this.target.length() == 0) {
        throw new InvalidParameterException("UDPBurstTask null target");
      }
    }

    /**
     * There are three UDP specific parameters:
     * 
     * 1. "direction": "up" if this is an uplink measurement. or "down"
     * otherwise 2. "packet_burst": how many packets should a up/down burst
     * have 3. "packet_size_byte": the size of each packet in bytes
     */
    @Override
    protected void initializeParams(Map<String, String> params) {
      if (params == null) {
        return;
      }

      this.target = params.get("target");


      try {
        String val = null;
        if ((val = params.get("packet_size_byte")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.packetSizeByte = Integer.parseInt(val);
          if (this.packetSizeByte < MIN_PACKETSIZE) {
            this.packetSizeByte = MIN_PACKETSIZE;
          }
          if (this.packetSizeByte > MAX_PACKETSIZE) {
            this.packetSizeByte = MAX_PACKETSIZE;
          }
        }
        if ((val = params.get("packet_burst")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.udpBurstCount = Integer.parseInt(val);
        }
        if ((val = params.get("dst_port")) != null && val.length() > 0
            && Integer.parseInt(val) > 0) {
          this.dstPort = Integer.parseInt(val);
        }
        if ((val = params.get("udp_interval")) != null
            && val.length() > 0 && Integer.parseInt(val) > 0) {
          this.udpInterval = Integer.parseInt(val);
        }
      } catch (NumberFormatException e) {
        throw new InvalidParameterException("UDPTask invalid params");
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
      return UDPBurstTask.TYPE;
    }
  }

  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return UDPBurstDesc.class;
  }

  public UDPBurstTask(MeasurementDesc desc, Context context) {
    super(new UDPBurstDesc(desc.key, desc.startTime, desc.endTime,
        desc.intervalSec, desc.count, desc.priority, desc.parameters), context);
    this.context = context;
    dataConsumed = 0;
  }

  /**
   * Make a deep cloning of the task
   */
  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    UDPBurstDesc newDesc = new UDPBurstDesc(desc.key, desc.startTime,
        desc.endTime, desc.intervalSec, desc.count, desc.priority,
        desc.parameters);
    return new UDPBurstTask(newDesc, context);
  }

  /**
   * Opens a datagram (UDP) socket
   * 
   * @return a datagram socket used for sending/receiving
   * @throws MeasurementError
   *             if an error occurs
   */
  private DatagramSocket openSocket() throws MeasurementError {
    DatagramSocket sock = null;

    // Open datagram socket
    try {
      sock = new DatagramSocket();
    } catch (SocketException e) {
      throw new MeasurementError("Socket creation failed");
    }

    return sock;
  }

  /**
   * @author Hongyi Yao (hyyao@umich.edu)
   * This class encapsulates the results of UDP burst measurement
   */
  private class UDPResult {
    public int packetNumber;
    public int InversionNumber;
    public long jitter;
    
    public UDPResult () {
      packetNumber = 0;
      InversionNumber = 0;
      jitter = 0L;
    }
  }

  /**
   * @author Hongyi Yao (hyyao@umich.edu)
   * This class calculates the number of inversion and delay jitter
   * in the array of received UDP packets
   */
  private class MetricCalculator {

    private int[] packetNumList;
    private long[] offsetedDelayList;
    private int size;
    private int inversionCounter;

    public MetricCalculator(int burstSize) {
      packetNumList = new int[burstSize];
      offsetedDelayList = new long[burstSize];
      size = 0;
      inversionCounter = 0;
    }

    public void addPacket(int packetNumber, long timestamp) {
      packetNumList[size] = packetNumber;
      offsetedDelayList[size] = System.currentTimeMillis()
          - timestamp;
      size++;
    }

    /**
     * Leverage the combine process during merge-sort to calculate inversion
     * number
     * @param start start point of the first array
     * @param mid the next to the end point of the first array 
   *            and the start point of the second one
     * @param end the next to the end point of the second array
     */
    private void combine(int start, int mid, int end) {
      int[] tmp = new int[end - start + 1];
      int pf = start;
      int ps = mid + 1;
      int pt = 0;// the number of sorted elements
      while (pf <= mid && ps <= end)
        if (packetNumList[pf] > packetNumList[ps]) {
          for (int t = pf; t <= mid; t++)
            inversionCounter++;
          tmp[pt++] = packetNumList[ps++];
        } else {
          tmp[pt++] = packetNumList[pf++];
        }
      while (pf <= mid)
        tmp[pt++] = packetNumList[pf++];
      while (ps <= end)
        tmp[pt++] = packetNumList[ps++];
      for (int i = start; i <= end; i++)
        packetNumList[i] = tmp[i - start];
    }

    /**
     * Recursively accumulate the inversion number  
     * @param start start point of the current array
     * @param end the next to end point of the current array
     */
    private void merge(int start, int end) {
      if (start < end) {
        int mid = (start + end) / 2;
        merge(start, mid);
        merge(mid + 1, end);
        combine(start, mid, end);
      }
    }

    /**
     * Get inversion number as the metric of UDP out-of-order count
     * @return the inversion number of the current UDP burst
     */
    public int calculateInversionNumber() {
      inversionCounter = 0;
      merge(0, size - 1);
      return inversionCounter;
    }

    /**
     * Calculate jitter as the standard deviation of one-way delays. 
     * Clock sync between client and server is not required since the clock
     * offset will be cancelled out during the calculation process
     * @return the jitter of UDP burst
     */
    public long calculateJitter() {
      if ( size > 1 ) {
        double offsetedDelay_mean = 0;
        for ( long offsetedDelay : offsetedDelayList ) {
          offsetedDelay_mean += (double)offsetedDelay / size;
        }

        double jitter = 0;
        for ( long offsetedDelay : offsetedDelayList ) {
          jitter += ((double)offsetedDelay - offsetedDelay_mean)
              * ((double)offsetedDelay - offsetedDelay_mean)  / (size - 1);
        }
        jitter = Math.sqrt(jitter);
        
        return (long)jitter;
      }
      else {
        return 0;
      }
    }
  }

  /**
   * Opens a Datagram socket to the server included in the UDPDesc and sends a
   * burst of UDPBurstCount packets, each of size packetSizeByte.
   * 
   * @return a Datagram socket that can be used to receive the server's
   *         response
   * 
   * @throws MeasurementError
   *             if an error occurred.
   */
  private DatagramSocket sendUpBurst() throws MeasurementError {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
    InetAddress addr = null;
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    DataOutputStream dataOut = new DataOutputStream(byteOut);
    DatagramSocket sock = null;

    // Resolve the server's name
    try {
      addr = InetAddress.getByName(desc.target);
      targetIp = addr.getHostAddress();
    } catch (UnknownHostException e) {
      throw new MeasurementError("Unknown host " + desc.target);
    }

    sock = openSocket();
    byte[] data = byteOut.toByteArray();
    DatagramPacket packet = new DatagramPacket(data, data.length, addr,
        desc.dstPort);
    // Send burst
    for (int i = 0; i < desc.udpBurstCount; i++) {
      byteOut.reset();
      try {
        dataOut.writeInt(UDPBurstTask.PKT_DATA);
        dataOut.writeInt(desc.udpBurstCount);
        dataOut.writeInt(i);

        dataOut.writeInt(-1); // left for inversion number
        // Sender: timestamp when sending
        // Receiver: jitter
        dataOut.writeLong(System.currentTimeMillis());

        dataOut.writeInt(desc.packetSizeByte);
        dataOut.writeInt(seq);
        dataOut.writeInt(0);  // udp interval invalid 
        for (int j = 0; j < desc.packetSizeByte
            - UDPBurstTask.MIN_PACKETSIZE; j++) {
          // Fill in the rest of the packet with zeroes.
          // TODO(aterzis): There has to be a better way to do this
          dataOut.write(0);
        }
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error creating message to "
            + desc.target);
      }
      data = byteOut.toByteArray();
      packet.setData(data);

      try {
        dataConsumed += packet.getLength();
        sock.send(packet);
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error sending " + desc.target);
      }
      Logger.i("Sent packet pnum:" + i + " to " + desc.target + ": "
          + targetIp);

      try {
        Thread.sleep(desc.udpInterval);
        Logger.i("UDP Burst sleep " + desc.udpInterval + "ms");
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    } // for()

    try {
      byteOut.close();
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error closing outputstream to: "
          + desc.target);
    }
    return sock;
  }

  /**
   * Receive a response from the server after the burst of uplink packets was
   * sent, parse it, and return the number of packets the server received.
   * 
   * @param sock
   *            the socket used to receive the server's response
   * @return the number of packets the server received
   * 
   * @throws MeasurementError
   *             if an error or a timeout occurs
   */
  private UDPResult recvUpResponse(DatagramSocket sock)
      throws MeasurementError {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
    int ptype, burstsize, pktnum, invnum;
    long jitter;

    UDPResult udpResult = new UDPResult();
    // Receive response
    Logger.i("Waiting for UDP response from " + desc.target + ": "
        + targetIp);

    byte buffer[] = new byte[UDPBurstTask.MIN_PACKETSIZE];
    DatagramPacket recvpacket = new DatagramPacket(buffer, buffer.length);

    try {
      sock.setSoTimeout(RCV_TIMEOUT);
      sock.receive(recvpacket);
    } catch (SocketException e1) {
      sock.close();
      throw new MeasurementError("Timed out reading from " + desc.target);
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error reading from " + desc.target);
    }

    ByteArrayInputStream byteIn = new ByteArrayInputStream(
        recvpacket.getData(), 0, recvpacket.getLength());
    DataInputStream dataIn = new DataInputStream(byteIn);
    dataConsumed += recvpacket.getLength();
    try {
      ptype = dataIn.readInt();
      burstsize = dataIn.readInt();
      pktnum = dataIn.readInt();
      invnum = dataIn.readInt();
      jitter = dataIn.readLong();
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error parsing response from "
          + desc.target);
    }

    Logger.i("Recv UDP resp from " + desc.target + " type:" + ptype
        + " burst:" + burstsize + " pktnum:" + pktnum + " invnum: "
        + invnum + " jitter: " + jitter);

    udpResult.packetNumber = pktnum;
    udpResult.InversionNumber = invnum;
    udpResult.jitter = jitter;
    return udpResult;
  }

  /**
   * Opens a datagram socket to the server in the UDPDesc and requests the
   * server to send a burst of UDPBurstCount packets, each of packetSizeByte
   * bytes.
   * 
   * @return the datagram socket used to receive the server's burst
   * @throws MeasurementError
   *             if an error occurs
   */
  private DatagramSocket sendDownRequest() throws MeasurementError {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
    DatagramPacket packet;
    InetAddress addr = null;
    ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
    DataOutputStream dataOut = new DataOutputStream(byteOut);
    DatagramSocket sock = null;

    // Resolve the server's name
    try {
      addr = InetAddress.getByName(desc.target);
      targetIp = addr.getHostAddress();
    } catch (UnknownHostException e) {
      throw new MeasurementError("Unknown host " + desc.target);
    }

    sock = openSocket();

    Logger.i("Requesting UDP burst:" + desc.udpBurstCount + " pktsize: "
        + desc.packetSizeByte + " to " + desc.target + ": " + targetIp);

    try {
      dataOut.writeInt(UDPBurstTask.PKT_REQUEST);
      dataOut.writeInt(desc.udpBurstCount);
      dataOut.writeInt(0);
      dataOut.writeInt(-1); // inv number is not used
      dataOut.writeLong(-1L); // timestamp is not used
      dataOut.writeInt(desc.packetSizeByte);
      dataOut.writeInt(seq);
      dataOut.writeInt(desc.udpInterval);  // set udp interval
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error creating message to "
          + desc.target);
    }

    byte[] data = byteOut.toByteArray();
    packet = new DatagramPacket(data, data.length, addr, desc.dstPort);

    try { 
      sock.send(packet);
      dataConsumed += packet.getLength();
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error sending " + desc.target);
    }

    try {
      byteOut.close();
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error closing Output Stream to:"
          + desc.target);
    }

    return sock;
  }

  /**
   * Receives a burst from the remote server and counts the number of packets
   * that were received.
   * 
   * @param sock
   *            the datagram socket that can be used to receive the server's
   *            burst
   * 
   * @return the number of packets received from the server
   * @throws MeasurementError
   *             if an error occurs
   */
  private UDPResult recvDownResponse(DatagramSocket sock)
      throws MeasurementError {    
    int pktrecv = 0;
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;

    MetricCalculator metricCalculator = new MetricCalculator(
        desc.udpBurstCount);

    // Receive response
    Logger.i("Waiting for UDP burst from " + desc.target);

    byte buffer[] = new byte[desc.packetSizeByte];
    DatagramPacket recvpacket = new DatagramPacket(buffer, buffer.length);

    for (int i = 0; i < desc.udpBurstCount; i++) {
      int ptype, burstsize, pktnum;
      long timestamp;

      try {
        sock.setSoTimeout(RCV_TIMEOUT);
        sock.receive(recvpacket);
      } catch (IOException e) {
        break;
      }

      ByteArrayInputStream byteIn = new ByteArrayInputStream(
          recvpacket.getData(), 0, recvpacket.getLength());
      DataInputStream dataIn = new DataInputStream(byteIn);


      dataConsumed += recvpacket.getLength();
      
      try {
        ptype = dataIn.readInt();
        burstsize = dataIn.readInt();
        pktnum = dataIn.readInt();
        dataIn.readInt(); // we do not need inversion number
        timestamp = dataIn.readLong(); // Get timestamp
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error parsing response from "
            + desc.target);
      }

      Logger.i("Recv UDP response from " + desc.target + " type:" + ptype
          + " burst:" + burstsize + " pktnum:" + pktnum
          + " timestamp:" + timestamp);

      if (ptype == UDPBurstTask.PKT_DATA) {
        pktrecv++;
        metricCalculator.addPacket(pktnum, timestamp);
      }
      
      try {
        byteIn.close();
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error closing input stream from "
            + desc.target);
      }
    } // for()

    UDPResult udpResult = new UDPResult();
    udpResult.packetNumber = pktrecv;
    udpResult.InversionNumber = metricCalculator.calculateInversionNumber();
    udpResult.jitter = metricCalculator.calculateJitter();
    return udpResult;
  }

  /**
   * Depending on the type of measurement, indicated by desc.Up, perform an
   * uplink/downlink measurement
   * 
   * @return the measurement's results
   * @throws MeasurementError
   *             if an error occurs
   */
  @Override
  public MeasurementResult call() throws MeasurementError {
    DatagramSocket socket = null;
    float response = 0.0F;
    UDPResult udpResult;
    int pktrecv = 0;
    boolean isMeasurementSuccessful = false;

    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
    
    if (!desc.target.equals(MLabNS.TARGET)) {
      throw new InvalidParameterException("Unknown target " + desc.target +
          " for UDPBurstTask");
    }

    ArrayList<String> mlabNSResult = MLabNS.Lookup(context,
        "mobiperf");
    if (mlabNSResult.size() == 1) {
      desc.target = mlabNSResult.get(0);
    } else {
      throw new InvalidParameterException("Invalid MLabNS query result"
          +
          " for UDPBurstTask");
    }
    Logger.i("Setting target to: " + desc.target);
    
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();

    Logger.i("Running UDPBurstTask on " + desc.target);
    try {
      if (desc.dirUp == true) {
        socket = sendUpBurst();
        udpResult = recvUpResponse(socket);
        pktrecv = udpResult.packetNumber;
        response = pktrecv / (float) desc.udpBurstCount;
        isMeasurementSuccessful = true;
      } else {
        socket = sendDownRequest();
        udpResult = recvDownResponse(socket);
        pktrecv = udpResult.packetNumber;
        response = pktrecv / (float) desc.udpBurstCount;
        isMeasurementSuccessful = true;
      }
    } catch (MeasurementError e) {
      throw e;
    } finally {
      socket.close();
    }

    MeasurementResult result = new MeasurementResult(
        phoneUtils.getDeviceInfo().deviceId,
        phoneUtils.getDeviceProperty(), UDPBurstTask.TYPE,
        System.currentTimeMillis() * 1000, isMeasurementSuccessful,
        this.measurementDesc);

    result.addResult("target_ip", targetIp);
    result.addResult("PRR", response);
    result.addResult("Inversion_Number", udpResult.InversionNumber);
    result.addResult("jitter", udpResult.jitter);
    // Update the sequence number to be used by the next burst
    seq++;
    return result;
  }

  @Override
  public String getType() {
    return UDPBurstTask.TYPE;
  }

  /**
   * Returns a brief human-readable descriptor of the task.
   */
  @Override
  public String getDescriptor() {
    return UDPBurstTask.DESCRIPTOR;
  }

  private void cleanUp() {
    // Do nothing
  }

  /**
   * Stop the measurement, even when it is running. Should release all
   * acquired resource in this function. There should not be side effect if
   * the measurement has not started or is already finished.
   */
  @Override
  public void stop() {
    cleanUp();
  }

  /**
   * This will be printed to the device log console. Make sure it's well
   * structured and human readable
   */
  @Override
  public String toString() {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
    String resp;

    if (desc.dirUp) {
      resp = "[UDPUp]\n";
    } else {
      resp = "[UDPDown]\n";
    }

    resp += " Target: " + desc.target + "\n  Interval (sec): "
        + desc.intervalSec + "\n  Next run: " + desc.startTime;

    return resp;
  }

  /**
   * Based on a direct accounting of UDP packet sizes.
   */
  @Override
  public long getDataConsumed() {
    return dataConsumed;
  }
}

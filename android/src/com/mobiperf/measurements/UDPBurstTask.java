// Copyright 2012 Google Inc. All Rights Reserved.


package com.mobiperf.measurements;

import com.mobiperf.speedometer.Logger;
import com.mobiperf.speedometer.MeasurementDesc;
import com.mobiperf.speedometer.MeasurementError;
import com.mobiperf.speedometer.MeasurementResult;
import com.mobiperf.speedometer.MeasurementTask;
import com.mobiperf.speedometer.SpeedometerApp;
import com.mobiperf.util.PhoneUtils;
import com.mobiperf.util.Util;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 
 * UDPBurstTask provides two types of measurements, Burst Up and Burst Down,
 * described next.
 * 
 * 1. UDPBurst Up: the device sends sends a burst of UDPBurstCount UDP packets
 * and waits for a response from the server that includes the number of
 * packets that the server received
 * 
 * 2. UDPBurst Down: the device sends a request to a remote server on a UDP port 
 * and the server responds by sending a burst of UDPBurstCount packets. 
 * The size of each packet is packetSizeByte
 */
public class UDPBurstTask extends MeasurementTask {

  public static final String TYPE = "udp_burst";
  public static final String DESCRIPTOR = "UDP Burst";

  private static final int DEFAULT_UDP_PACKET_SIZE = 100;
  private static final int DEFAULT_UDP_BURST = 10;
  private static final int MIN_PACKETSIZE = 20;
  private static final int MAX_PACKETSIZE = 500;
  private static final int DEFAULT_PORT = 31341;

  private static final int RCV_TIMEOUT = 3000; // in msec

  private static final int PKT_ERROR = 1;
  private static final int PKT_RESPONSE = 2;
  private static final int PKT_DATA = 3;
  private static final int PKT_REQUEST = 4;

  private String targetIp = null;
  private Context context = null;

  private static int seq = 1;

  /**
   * Encode UDP specific parameters, along with common parameters 
   * inherited from MeasurementDesc
   * 
   */
  public static class UDPBurstDesc extends MeasurementDesc {     
    // Declare static parameters specific to SampleMeasurement here

    public int packetSizeByte = UDPBurstTask.DEFAULT_UDP_PACKET_SIZE;  
    public int udpBurstCount = UDPBurstTask.DEFAULT_UDP_BURST;
    public int dstPort = UDPBurstTask.DEFAULT_PORT;
    public String target = null;
    public boolean dirUp = false;
    private Context context = null;

    public UDPBurstDesc(String key, Date startTime,
        Date endTime, double intervalSec, long count, 
        long priority, Map<String, String> params, Context context) 
            throws InvalidParameterException {
      super(UDPBurstTask.TYPE, key, startTime, endTime, intervalSec, count,
          priority, params);
      this.context = context;
      initializeParams(params);
      if (this.target == null || this.target.length() == 0) {
        throw new InvalidParameterException("UDPBurstTask null target");
      }
    }

    private String getContentCharSet(final HttpEntity entity) throws ParseException {
      if (entity == null) {
        throw new IllegalArgumentException("entity may not be null");
      }

      String charset = null;
      if (entity.getContentType() != null) {
        HeaderElement values[] = entity.getContentType().getElements();
        if (values.length > 0) {
          NameValuePair param = values[0].getParameterByName("charset");
          if (param != null) {
            charset = param.getValue();
          }
        }
      }
      return charset;
    }

    private String getResponseBodyFromEntity(HttpEntity entity) throws IOException, ParseException {
      if (entity == null) {
        throw new IllegalArgumentException("entity may not be null");
      }

      InputStream instream = entity.getContent();
      if (instream == null) {
        return "";
      }

      if (entity.getContentEncoding() != null) {
        if ("gzip".equals(entity.getContentEncoding().getValue())) {
          instream = new GZIPInputStream(instream);
        }
      }

      if (entity.getContentLength() > Integer.MAX_VALUE) {
        throw new IllegalArgumentException("HTTP entity too large to be buffered into memory");
      }

      String charset = getContentCharSet(entity);
      if (charset == null) {
        charset = HTTP.DEFAULT_CONTENT_CHARSET;
      }

      Reader reader = new InputStreamReader(instream, charset);
      StringBuilder buffer = new StringBuilder();

      try {
        char[] tmp = new char[1024];
        int l;
        while ((l = reader.read(tmp, 0, tmp.length)) != -1) {
          Logger.d("  reading: " + tmp);
          buffer.append(tmp);
        }
      } finally {
        reader.close();
      }
      
      return buffer.toString();
    }

    private String getResponseBody(HttpResponse response) throws IllegalArgumentException {
      String response_text = null;
      HttpEntity entity = null;
      
      if (response == null) {
        throw new IllegalArgumentException("response may not be null");
      }

      try {
        entity = response.getEntity();
        response_text = getResponseBodyFromEntity(entity);
      } catch (ParseException e) {
        e.printStackTrace();
      } catch (IOException e) {
        if (entity != null) {
          try {
            entity.consumeContent();
          } catch (IOException e1) {
          }
        }
      }
      return response_text;
    }

    /**
     * There are three UDP specific parameters:
     * 
     * 1. "direction": "up" if this is an uplink measurement. or "down" otherwise
     * 2. "packet_burst": how many packets should a up/down burst have
     * 3. "packet_size_byte": the size of each packet in bytes
     */
    @Override
    protected void initializeParams(Map<String, String> params) {
      if (params == null) {
        return;
      }

      this.target = params.get("target");
      if (!this.target.equals("m-lab")) {
        throw new InvalidParameterException("Unknown target " + target + " for UDPBurstTask");
      }

      // TODO(dominich): Abstract this out.
      // Query m-lab-ns for a domain to use.
      final int maxResponseSize = 1024;

      ByteBuffer body = ByteBuffer.allocate(maxResponseSize);
      InputStream inputStream = null;
    
      try {
        // TODO(Wenjie): Need to set timeout for the HTTP methods
        // TODO(dominich): This should not be done on the UI thread.
        DefaultHttpClient httpClient = new DefaultHttpClient();
        Logger.d("Creating request GET for mlab-ns");
        // TODO(dominich): Remove address_family and allow for IPv6.
        HttpGet request = new HttpGet("http://mlab-ns.appspot.com/mobiperf?format=json&address_family=ipv4");
        request.setHeader("User-Agent", Util.prepareUserAgent(this.context));

        HttpResponse response = httpClient.execute(request);

        /* TODO(Wenjie): HttpClient does not automatically handle the following codes
         * 301 Moved Permanently. HttpStatus.SC_MOVED_PERMANENTLY
         * 302 Moved Temporarily. HttpStatus.SC_MOVED_TEMPORARILY
         * 303 See Other. HttpStatus.SC_SEE_OTHER
         * 307 Temporary Redirect. HttpStatus.SC_TEMPORARY_REDIRECT
         * 
         * We may want to fetch instead from the redirected page. 
         */
        if (response.getStatusLine().getStatusCode() != 200) {
          throw new InvalidParameterException(
              "Received status " + response.getStatusLine().getStatusCode() + " from m-lab-ns");
        }
        Logger.d("STATUS OK");

        String body_str = getResponseBody(response);
        Logger.i("Received from m-lab-ns: " + body_str);
        JSONObject json = new JSONObject(body_str);
        this.target = String.valueOf(json.getString("fqdn"));
        Logger.i("Setting target to: " + this.target);
      } catch (IOException e) {
        Logger.e("IOException trying to contact m-lab-ns: " + e.getMessage());
        throw new InvalidParameterException(e.getMessage());
      } catch (JSONException e) {
        Logger.e("JSONException trying to contact m-lab-ns: " + e.getMessage());
        throw new InvalidParameterException(e.getMessage());
      } finally {
        if (inputStream != null) {
          try {
            inputStream.close();
          } catch (IOException e) {
            Logger.e("Failed to close the input stream from the HTTP response");
          }
        }
      }

      try {
        String val = null;
        if ((val = params.get("packet_size_byte")) != null && val.length() > 0 
            && Integer.parseInt(val) > 0) {
          this.packetSizeByte = Integer.parseInt(val);
          if (this.packetSizeByte < MIN_PACKETSIZE) {
            this.packetSizeByte = MIN_PACKETSIZE;
          }
          if (this.packetSizeByte > MAX_PACKETSIZE) {
            this.packetSizeByte = MAX_PACKETSIZE;
          }
        }
        if ((val = params.get("packet_burst")) != null && val.length() > 0 &&
            Integer.parseInt(val) > 0) {
          this.udpBurstCount = Integer.parseInt(val);  
        }
        if ((val = params.get("dst_port")) != null && val.length() > 0 
            && Integer.parseInt(val) > 0) {
          this.dstPort = Integer.parseInt(val);
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
        desc.intervalSec, desc.count, desc.priority, desc.parameters, context),
        context);
    this.context = context;
  }

  /**
   * Make a deep cloning of the task
   */
  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    UDPBurstDesc newDesc = new UDPBurstDesc(desc.key, desc.startTime, 
        desc.endTime, desc.intervalSec, desc.count, desc.priority, 
        desc.parameters, context);
    return new UDPBurstTask(newDesc, context);
  }
 
  /**
   * Opens a datagram (UDP) socket
   * 
   * @return a datagram socket used for sending/receiving
   * @throws MeasurementError if an error occurs
   */
  private DatagramSocket openSocket() throws MeasurementError {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
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
   * Opens a Datagram socket to the server included in the UDPDesc
   * and sends a burst of UDPBurstCount packets, each of size packetSizeByte.
   * 
   * @return a Datagram socket that can be used to receive the 
   * server's response
   * 
   * @throws MeasurementError if an error occurred.
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
    DatagramPacket packet = new DatagramPacket(data, data.length, addr, desc.dstPort);
    // Send burst
    for (int i = 0; i < desc.udpBurstCount; i++) {
      byteOut.reset();
      try {
        dataOut.writeInt(UDPBurstTask.PKT_DATA);
        dataOut.writeInt(desc.udpBurstCount);
        dataOut.writeInt(i);
        dataOut.writeInt(desc.packetSizeByte);
        dataOut.writeInt(seq);
        for (int j = 0; j < desc.packetSizeByte - UDPBurstTask.MIN_PACKETSIZE; j++) {
          // Fill in the rest of the packet with zeroes.
          // TODO(aterzis): There has to be a better way to do this
          dataOut.write(0);
        }
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error creating message to " + desc.target);
      }
      data = byteOut.toByteArray();
      packet.setData(data);
 
      try {
        sock.send(packet);
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error sending " + desc.target);
      }
      Log.i(SpeedometerApp.TAG, "Sent packet pnum:" + i + " to " + desc.target + ": " + targetIp);
    } // for()

    try {
      byteOut.close();
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error closing outputstream to: " + desc.target);
    }
    return sock;
  }
 
  /**
   * Receive a response from the server after the burst of uplink packets was
   * sent, parse it, and return the number of packets the server received.
   * 
   * @param sock the socket used to receive the server's response
   * @return the number of packets the server received
   * 
   * @throws MeasurementError if an error or a timeout occurs
   */
  private int recvUpResponse(DatagramSocket sock) throws MeasurementError {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
    int ptype, burstsize, pktnum;
 
    // Receive response
    Log.i(SpeedometerApp.TAG, "Waiting for UDP response from " + desc.target + ": " + targetIp);

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

    ByteArrayInputStream byteIn =
        new ByteArrayInputStream(recvpacket.getData(), 0, recvpacket.getLength());
    DataInputStream dataIn = new DataInputStream(byteIn);
 
    try {
      ptype = dataIn.readInt();
      burstsize = dataIn.readInt();
      pktnum = dataIn.readInt();
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error parsing response from " + desc.target);
    }
 
    Log.i(SpeedometerApp.TAG, "Recv UDP resp from " + desc.target + " type:" + ptype + " burst:"
        + burstsize + " pktnum:" + pktnum);
 
    return pktnum;
  }
 
  /**
   * Opens a datagram socket to the server in the UDPDesc and requests the
   * server to send a burst of UDPBurstCount packets, each of packetSizeByte
   * bytes. 
   * 
   * @return the datagram socket used to receive the server's burst
   * @throws MeasurementError if an error occurs
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

    Log.i(SpeedometerApp.TAG, "Requesting UDP burst:" + desc.udpBurstCount + " pktsize: " 
        + desc.packetSizeByte + " to " + desc.target + ": " + targetIp);

    try {
      dataOut.writeInt(UDPBurstTask.PKT_REQUEST);
      dataOut.writeInt(desc.udpBurstCount);
      dataOut.writeInt(0);
      dataOut.writeInt(desc.packetSizeByte);
      dataOut.writeInt(seq);
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error creating message to " + desc.target);
    }

    byte []data = byteOut.toByteArray();
    packet = new DatagramPacket(data, data.length, addr, desc.dstPort);

    try { 
      sock.send(packet);
    } catch (IOException e) {
      sock.close();
      throw new MeasurementError("Error sending " + desc.target);
    }

    try {
      byteOut.close();
    } catch (IOException e){
      sock.close();
      throw new MeasurementError("Error closing Output Stream to:" + desc.target);
    }

    return sock;
  }
  
  /**
   * Receives a burst from the remote server and counts the number of packets
   * that were received. 
   * 
   * @param sock the datagram socket that can be used to receive the server's
   * burst
   * 
   * @return the number of packets received from the server
   * @throws MeasurementError if an error occurs
   */
  private int recvDownResponse(DatagramSocket sock) throws MeasurementError {
    int pktrecv = 0;
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;

    // Receive response
    Log.i(SpeedometerApp.TAG, "Waiting for UDP burst from " + desc.target);

    byte buffer[] = new byte[desc.packetSizeByte];
    DatagramPacket recvpacket = new DatagramPacket(buffer, buffer.length);

    for (int i = 0; i < desc.udpBurstCount; i++) {
      int ptype, burstsize, pktnum; 

      try {
        sock.setSoTimeout (RCV_TIMEOUT);
        sock.receive (recvpacket);
      } catch (IOException e) {
        break;
      }

      ByteArrayInputStream byteIn = 
          new ByteArrayInputStream (recvpacket.getData (), 0, 
              recvpacket.getLength ());
      DataInputStream dataIn = new DataInputStream (byteIn);

      try {
        ptype = dataIn.readInt();
        burstsize = dataIn.readInt();
        pktnum = dataIn.readInt();
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error parsing response from " + desc.target); 
      }

      Log.i(SpeedometerApp.TAG, "Recv UDP response from " + desc.target + 
          " type:" + ptype + " burst:" + burstsize + " pktnum:" + pktnum);

      if (ptype == UDPBurstTask.PKT_DATA) {
        pktrecv++;
      }

      try {
        byteIn.close();
      } catch (IOException e) {
        sock.close();
        throw new MeasurementError("Error closing input stream from " + desc.target);
      }
    } // for()

    return pktrecv;
  }

  /**
   * Depending on the type of measurement, indicated by desc.Up, perform
   * an uplink/downlink measurement 
   * 
   * @return the measurement's results
   * @throws MeasurementError if an error occurs
   */
  @Override
  public MeasurementResult call() throws MeasurementError {
    DatagramSocket socket = null;
    DatagramPacket packet;
    float response = 0.0F;
    int pktrecv = 0;
    boolean isMeasurementSuccessful = false;

    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;
    PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();

    Logger.i("Running UDPBurstTask on " + desc.target);
    try {
      if (desc.dirUp == true) {
        socket = sendUpBurst();
        pktrecv = recvUpResponse(socket);
        response = pktrecv / (float) desc.udpBurstCount;
        isMeasurementSuccessful = true;
      } else {
        socket = sendDownRequest();
        pktrecv = recvDownResponse(socket);
        response = pktrecv / (float) desc.udpBurstCount;
        isMeasurementSuccessful = true;
      }
    } catch (MeasurementError e) {
      throw e;
    } finally {
      socket.close();      
    }
    
    MeasurementResult result = new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
        phoneUtils.getDeviceProperty(), UDPBurstTask.TYPE, 
        System.currentTimeMillis() * 1000, isMeasurementSuccessful, 
        this.measurementDesc);
 
        result.addResult("target_ip", targetIp);
        result.addResult("PRR", response);
        // Update the sequence number to be used by the next burst
        seq++;
        return result;
  }

  @Override
  public String getType() {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;

      return UDPBurstTask.TYPE;
  }

  /**
   * Returns a brief human-readable descriptor of the task.
   */
  @Override
  public String getDescriptor() {
    UDPBurstDesc desc = (UDPBurstDesc) measurementDesc;   
      return UDPBurstTask.DESCRIPTOR;
  }

  private void cleanUp() {
    // Do nothing
  }


  /**
   * Stop the measurement, even when it is running. Should release all acquired
   * resource in this function. There should not be side effect if the measurement 
   * has not started or is already finished.
   */
  @Override
  public void stop() {
    cleanUp();
  }

  /**
   * This will be printed to the device log console. Make sure it's well structured and
   * human readable
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
      
    resp += " Target: " + desc.target + "\n  Interval (sec): " + 
    desc.intervalSec + "\n  Next run: " + desc.startTime;
    
    return resp;
  }
}

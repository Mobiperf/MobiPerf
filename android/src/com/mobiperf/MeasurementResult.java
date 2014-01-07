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
package com.mobiperf;

import android.util.StringBuilderPrinter;

import com.mobiperf.measurements.DnsLookupTask;
import com.mobiperf.measurements.DnsLookupTask.DnsLookupDesc;
import com.mobiperf.measurements.HttpTask;
import com.mobiperf.measurements.HttpTask.HttpDesc;
import com.mobiperf.measurements.PingTask;
import com.mobiperf.measurements.PingTask.PingDesc;
import com.mobiperf.measurements.TracerouteTask;
import com.mobiperf.measurements.TracerouteTask.TracerouteDesc;
import com.mobiperf.measurements.UDPBurstTask;
import com.mobiperf.measurements.UDPBurstTask.UDPBurstDesc;
import com.mobiperf.measurements.TCPThroughputTask;
import com.mobiperf.measurements.TCPThroughputTask.TCPThroughputDesc;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;
import com.mobiperf.util.Util;

import java.util.Formatter;
import java.util.HashMap;

/**
 * POJO that represents the result of a measurement
 * @see MeasurementDesc
 */
public class MeasurementResult {   

  private String deviceId;
  private DeviceProperty properties;
  private long timestamp;
  private boolean success;
  private String taskKey;
  private String type;
  private MeasurementDesc parameters;
  private HashMap<String, String> values;
  
  /**
   * @param deviceProperty
   * @param type
   * @param timestamp
   * @param success
   * @param measurementDesc
   */
  public MeasurementResult(String id, DeviceProperty deviceProperty, String type, 
                           long timeStamp, boolean success, MeasurementDesc measurementDesc) {
    super();
    this.taskKey = measurementDesc.key;
    this.deviceId = id;
    this.type = type;
    this.properties = deviceProperty;
    this.timestamp = timeStamp;
    this.success = success;
    this.parameters = measurementDesc;
    this.parameters.parameters = null;
    this.values = new HashMap<String, String>();
  }
 
  /* Returns the type of this result */ 
  public String getType() {
    return parameters.getType();
  }
  
  /* Add the measurement results of type String into the class */
  public void addResult(String resultType, Object resultVal) {
    this.values.put(resultType, MeasurementJsonConvertor.toJsonString(resultVal));
  }
  
  /* Returns a string representation of the result */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    StringBuilderPrinter printer = new StringBuilderPrinter(builder);
    Formatter format = new Formatter();
    try {
      if (type == PingTask.TYPE) {
        getPingResult(printer, values);
      } else if (type == HttpTask.TYPE) {
        getHttpResult(printer, values);
      } else if (type == DnsLookupTask.TYPE) {
        getDnsResult(printer, values);
      } else if (type == TracerouteTask.TYPE) {
        getTracerouteResult(printer, values);
      } else if (type == UDPBurstTask.TYPE) {
        getUDPBurstResult(printer, values);
      } else if (type == TCPThroughputTask.TYPE) {
        getTCPThroughputResult(printer, values);
      } else {
        Logger.e("Failed to get results for unknown measurement type " + type);
      }
      return builder.toString();
    } catch (NumberFormatException e) {
      Logger.e("Exception occurs during constructing result string for user", e);
    } catch (ClassCastException e) {
      Logger.e("Exception occurs during constructing result string for user", e);
    } catch (Exception e) {
      Logger.e("Exception occurs during constructing result string for user", e);
    }
    return "Measurement has failed";
  }
  
  private void getPingResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    PingDesc desc = (PingDesc) parameters;
    printer.println("[Ping]");
    printer.println("Target: " + desc.target);
    String ipAddress = removeQuotes(values.get("target_ip"));
    // TODO: internationalize 'Unknown'.
    if (ipAddress == null) {
      ipAddress = "Unknown";
    }
    printer.println("IP address: " + ipAddress);
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    printIPTestResult(printer);
    
    if (success) {
      float packetLoss = Float.parseFloat(values.get("packet_loss"));
      int count = Integer.parseInt(values.get("packets_sent"));
      printer.println("\n" + count + " packets transmitted, " + (int) (count * (1 - packetLoss)) + 
          " received, " + (packetLoss * 100) + "% packet loss");

      float value = Float.parseFloat(values.get("mean_rtt_ms"));
      printer.println("Mean RTT: " + String.format("%.1f", value) + " ms");
    
      value = Float.parseFloat(values.get("min_rtt_ms"));
      printer.println("Min RTT:  " + String.format("%.1f", value) + " ms");
    
      value = Float.parseFloat(values.get("max_rtt_ms"));
      printer.println("Max RTT:  " + String.format("%.1f", value) + " ms");
    
      value = Float.parseFloat(values.get("stddev_rtt_ms"));
      printer.println("Std dev:  " + String.format("%.1f", value) + " ms");
    } else {
      printer.println("Failed");
    }
  }
  
  private void getHttpResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    HttpDesc desc = (HttpDesc) parameters;
    printer.println("[HTTP]");
    printer.println("URL: " + desc.url);
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    printIPTestResult(printer);
    
    if (success) {
      int headerLen = Integer.parseInt(values.get("headers_len"));
      int bodyLen = Integer.parseInt(values.get("body_len"));
      int time = Integer.parseInt(values.get("time_ms"));
      printer.println("");
      printer.println("Downloaded " + (headerLen + bodyLen) + " bytes in " + time + " ms");
      printer.println("Bandwidth: " + (headerLen + bodyLen) * 8 / time + " Kbps");
    } else {
      printer.println("Download failed, status code " + values.get("code"));
    }
  }
  
  private void getDnsResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    DnsLookupDesc desc = (DnsLookupDesc) parameters;
    printer.println("[DNS Lookup]");
    printer.println("Target: " + desc.target);
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    printIPTestResult(printer);
    
    if (success) {
      String ipAddress = removeQuotes(values.get("address"));
      if (ipAddress == null) {
        ipAddress = "Unknown";
      }
      printer.println("\nAddress: " + ipAddress);
      int time = Integer.parseInt(values.get("time_ms"));
      printer.println("Lookup time: " + time + " ms");
    } else {
      printer.println("Failed");
    }
  }
  
  private void getTracerouteResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    TracerouteDesc desc = (TracerouteDesc) parameters;
    printer.println("[Traceroute]");
    printer.println("Target: " + desc.target);
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    printIPTestResult(printer);
    
    if (success) {
      // Manually inject a new line
      printer.println(" ");
    
      int hops = Integer.parseInt(values.get("num_hops"));
      int hop_str_len = String.valueOf(hops + 1).length();
      for (int i = 0; i < hops; i++) {
        String key = "hop_" + i + "_addr_1";
        String ipAddress = removeQuotes(values.get(key));
        if (ipAddress == null) {
          ipAddress = "Unknown";
        }
        String hop_str = String.valueOf(i+1);
        String hopInfo = hop_str;
        for (int j = 0; j < hop_str_len + 1 - hop_str.length(); ++j) {
          hopInfo += " ";
        }
        hopInfo += ipAddress;
        // Maximum IP address length is 15.
        for (int j = 0; j < 16 - ipAddress.length(); ++j) {
          hopInfo += " ";
        }
      
        key = "hop_" + i + "_rtt_ms";
        // The first and last character of this string are double quotes.
        String timeStr = removeQuotes(values.get(key));
        if (timeStr == null) {
          timeStr = "Unknown";
        }
      
        float time = Float.parseFloat(timeStr);
        printer.println(hopInfo + String.format("%6.2f", time) + " ms");
      }
    } else {
      printer.println("Failed");
    }
  }
  
  private void getUDPBurstResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    UDPBurstDesc desc = (UDPBurstDesc) parameters;
    if (desc.dirUp) {
      printer.println("[UDPBurstUp]");
    } else {
      printer.println("[UDPBurstDown]");
    }
    printer.println("Target: " + desc.target);
    printer.println("IP addr: " + values.get("target_ip"));
    if (success) {
      printer.println("Timestamp: " + 
        Util.getTimeStringFromMicrosecond(properties.timestamp));
      printIPTestResult(printer);
      printer.println("Packet size: " + desc.packetSizeByte + "B");
      printer.println("Number of packets to be sent: " + desc.udpBurstCount);
      printer.println("Interval between packets: " + desc.udpInterval + "ms");

      String lossRatio = String.format("%.2f"
        , Double.parseDouble(values.get("loss_ratio")) * 100);
      String outOfOrderRatio = String.format("%.2f"
        , Double.parseDouble(values.get("out_of_order_ratio")) * 100);
      printer.println("\nLoss ratio: " + lossRatio + "%");
      printer.println("Out of order ratio: " + outOfOrderRatio + "%");
      printer.println("Jitter: " + values.get("jitter") + "ms");
    } else {
      printer.println("Failed");
    }
  }

  private void getTCPThroughputResult(StringBuilderPrinter printer, 
                                      HashMap<String, String> values) {
    TCPThroughputDesc desc = (TCPThroughputDesc) parameters;
    if (desc.dir_up) {
      printer.println("[TCP Uplink]");
    } else {
      printer.println("[TCP Downlink]");
    }
    printer.println("Target: " + desc.target);
    printer.println("Timestamp: " +
        Util.getTimeStringFromMicrosecond(properties.timestamp));
    printIPTestResult(printer);
    
    if (success) {
      printer.println("");
      // Display result with precision up to 2 digit
      String speedInJSON = values.get("tcp_speed_results");
      String dataLimitExceedInJSON = values.get("data_limit_exceeded");
      String displayResult = "";

      double tp = desc.calMedianSpeedFromTCPThroughputOutput(speedInJSON);
      double KB = Math.pow(2, 10);
      if (tp < 0) {
        displayResult = "No results available.";
      } else if (tp > KB*KB) {
        displayResult = "Speed: " + String.format("%.2f",tp/(KB*KB)) + " Gbps";
      } else if (tp > KB ) {
        displayResult = "Speed: " + String.format("%.2f",tp/KB) + " Mbps";
      } else {
        displayResult = "Speed: " + String.format("%.2f", tp) + " Kbps";
      }

      // Append notice for exceeding data limit
      if (dataLimitExceedInJSON.equals("true")) {
        displayResult += "\n* Task finishes earlier due to exceeding " +
                         "maximum number of "+ ((desc.dir_up) ? "transmitted" : "received") + 
                         " bytes";
      }
      printer.println(displayResult);
    } else {
      printer.println("Failed");
    }
  }
  
  /**
   * Removes the quotes surrounding the string. If |str| is null, returns null.
   */
  private String removeQuotes(String str) {
    return str != null ? str.replaceAll("^\"|\"", "") : null;
  }
  
  /**
   * Print ip connectivity and hostname resolvability result
   */
  private void printIPTestResult (StringBuilderPrinter printer) {
    printer.println("IPv4/IPv6 Connectivity: " + properties.ipConnectivity);
    printer.println("IPv4/IPv6 Domain Name Resolvability: " 
                    + properties.dnResolvability);
  }
}

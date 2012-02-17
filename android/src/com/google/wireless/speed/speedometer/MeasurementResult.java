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
package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.measurements.DnsLookupTask;
import com.google.wireless.speed.speedometer.measurements.DnsLookupTask.DnsLookupDesc;
import com.google.wireless.speed.speedometer.measurements.HttpTask;
import com.google.wireless.speed.speedometer.measurements.HttpTask.HttpDesc;
import com.google.wireless.speed.speedometer.measurements.PingTask;
import com.google.wireless.speed.speedometer.measurements.PingTask.PingDesc;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask;
import com.google.wireless.speed.speedometer.measurements.TracerouteTask.TracerouteDesc;
import com.google.wireless.speed.speedometer.measurements.UDPBurstTask;
import com.google.wireless.speed.speedometer.measurements.UDPBurstTask.UDPBurstDesc;
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;
import com.google.wireless.speed.speedometer.util.Util;

import android.util.Log;
import android.util.StringBuilderPrinter;

import java.util.Formatter;
import java.util.HashMap;

/**
 * POJO that represents the result of a measurement
 * @author wenjiezeng@google.com (Wenjie Zeng)
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
    printer.println("IP address: " + removeQuotes(values.get("target_ip")));
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    
    float packetLoss = Float.parseFloat(values.get("packet_loss"));
    int count = Integer.parseInt(values.get("packets_sent"));
    printer.println("\n" + count + " packets transmitted, " + (int) (count * (1 - packetLoss)) + 
        " received, " + (packetLoss * 100) + "% packet loss");
    
    float value = Float.parseFloat(values.get("mean_rtt_ms"));
    printer.println("Mean RTT: " + String.format("%.1f", value) + " ms");
    
    value = Float.parseFloat(values.get("min_rtt_ms"));
    printer.println("Min RTT: " + String.format("%.1f", value) + " ms");
    
    value = Float.parseFloat(values.get("max_rtt_ms"));
    printer.println("Max RTT: " + String.format("%.1f", value) + " ms");
    
    value = Float.parseFloat(values.get("stddev_rtt_ms"));
    printer.println("Std dev: " + String.format("%.1f", value) + " ms");
  }
  
  private void getHttpResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    HttpDesc desc = (HttpDesc) parameters;
    printer.println("[HTTP]");
    printer.println("URL: " + desc.url);
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    
    int headerLen = Integer.parseInt(values.get("headers_len"));
    int bodyLen = Integer.parseInt(values.get("body_len"));
    int time = Integer.parseInt(values.get("time_ms"));
    
    printer.println("\nDownloaded " + (headerLen + bodyLen) + " bytes in " + time + " ms");
    printer.println("Bandwidth: " + (headerLen + bodyLen) * 8 / time + " Kbps");
  }
  
  private void getDnsResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    DnsLookupDesc desc = (DnsLookupDesc) parameters;
    printer.println("[DNS Lookup]");
    printer.println("Target: " + desc.target);
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    
    String ipAddress = removeQuotes(values.get("address"));
    if (ipAddress == null) {
      ipAddress = "Unknown";
    }
    printer.println("\nAddress: " + ipAddress);
    int time = Integer.parseInt(values.get("time_ms"));
    printer.println("Lookup time: " + time + " ms");
  }
  
  private void getTracerouteResult(StringBuilderPrinter printer, HashMap<String, String> values) {
    TracerouteDesc desc = (TracerouteDesc) parameters;
    printer.println("[Traceroute]");
    printer.println("Target: " + desc.target);
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
    // Manually inject a new line
    printer.println(" ");
    
    int hops = Integer.parseInt(values.get("num_hops"));
    for (int i = 0; i < hops; i++) {
      String key = "hop_" + i + "_addr_1";
      String ipAddress = removeQuotes(values.get(key));
      if (ipAddress == null) {
        ipAddress = "Unknown";
      }
      String hopInfo = (i + 1) + " " + ipAddress;
      
      key = "hop_" + i + "_rtt_ms";
      // The first and last character of this string are double quotes.
      String timeStr = removeQuotes(values.get(key));
      if (timeStr == null) {
        timeStr = "Unknown";
      }
      
      float time = Float.parseFloat(timeStr);
      printer.println(hopInfo + "\t\t" + String.format("%.1f", time) + " ms");
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
    printer.println("PRR: " + values.get("PRR"));
    printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
  }

  
  /**
   * Removes the quotes surrounding the string. If the string is less than 2 in length,
   * we returns null
   */
  private String removeQuotes(String str) {
    if (str != null && str.length() > 2) {
      return str.substring(1, str.length() - 2);
    } else {
      return null;
    }
  }
}
 
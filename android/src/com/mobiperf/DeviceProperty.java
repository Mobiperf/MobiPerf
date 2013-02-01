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



/**
 * POJO class containing dynamic information about the device
 * @see DeviceInfo
 */
public class DeviceProperty {

  public String deviceId;
  public String appVersion;
  public long timestamp;
  public String osVersion;
  public String ipConnectivity;
  public String dnResolvability;
  public GeoLocation location;
  public String locationType;
  public String networkType;
  public String carrier;
  public int batteryLevel;
  public boolean isBatteryCharging;
  public String cellInfo;
  public int rssi;

  public DeviceProperty(String deviceId, String appVersion, long timeStamp, 
      String osVersion, String ipConnectivity, String dnResolvability, 
      double longtitude, double latitude, String locationType, 
      String networkType, String carrier, int batteryLevel, boolean isCharging,
      String cellInfo, int rssi) {
    super();
    this.deviceId = deviceId;
    this.appVersion = appVersion;
    this.timestamp = timeStamp;
    this.osVersion = osVersion;
    this.ipConnectivity = ipConnectivity;
    this.dnResolvability = dnResolvability;
    this.location = new GeoLocation(longtitude, latitude);
    this.locationType = locationType;
    this.networkType = networkType;
    this.carrier = carrier;
    this.batteryLevel = batteryLevel;
    this.isBatteryCharging = isCharging;
    this.cellInfo = cellInfo;
    this.rssi = rssi;
  }
  
  private class GeoLocation {
    private double longitude;
    private double latitude;
    
    public GeoLocation(double longtitude, double latitude) {
      this.longitude = longtitude;
      this.latitude = latitude;
    }
  }
}

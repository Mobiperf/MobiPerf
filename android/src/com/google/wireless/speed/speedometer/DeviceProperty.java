// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import java.util.Date;


/**
 * POJO class containing dynamic information about the device
 * @see DeviceInfo
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class DeviceProperty {

  public String deviceId;
  public String appVersion;
  public Date timestamp;
  public String osVersion;
  public String ipAddress;
  public GeoLocation location;
  public String locationType;
  public String networkType;
  public String carrier;
  public int batteryLevel;
  public boolean isBatteryCharging;

  public DeviceProperty(String deviceId, String appVersion, Date timeStamp, String osVersion,
      String ipAddress, double longtitude, double latitude, String locationType, 
      String networkType, String carrier, int batteryLevel, boolean isCharging) {
    super();
    this.deviceId = deviceId;
    this.appVersion = appVersion;
    this.timestamp = timeStamp;
    this.osVersion = osVersion;
    this.ipAddress = ipAddress;    
    this.location = new GeoLocation(longtitude, latitude);
    this.locationType = locationType;
    this.networkType = networkType;
    this.carrier = carrier;
    this.batteryLevel = batteryLevel;
    this.isBatteryCharging = isCharging;
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

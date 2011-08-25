// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer.util;

import com.google.wireless.speed.speedometer.DeviceInfo;
import com.google.wireless.speed.speedometer.DeviceProperty;
import com.google.wireless.speed.speedometer.PhoneUtils;
import com.google.wireless.speed.speedometer.R;
import com.google.wireless.speed.speedometer.SpeedometerApp;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Enumeration;

/**
 * Runtime utility class for Speedometer that requires runtime (activity context) information
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class RuntimeUtil {
  
  private static Activity activity;
  private static DeviceInfo deviceInfo;   
  
  /* TODO(Wenjie): People can forget the acitivty initialization here. Use a singleton to 
   * avoid that. */
  public static void setActivity(Activity act) {
    activity = act;
  }
  
  private static String getVersionStr() {
    return String.format("INCREMENTAL:%s, RELEASE:%s, SDK_INT:%s", Build.VERSION.INCREMENTAL,
        Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
  }
  
  public static DeviceInfo getDeviceInfo() {
    if (deviceInfo == null) {
      deviceInfo = new DeviceInfo();
      TelephonyManager tManager = 
          (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
      deviceInfo.deviceId = tManager.getDeviceId();
      deviceInfo.manufacturer = Build.MANUFACTURER;
      deviceInfo.model = Build.MODEL;
      deviceInfo.os = getVersionStr();
      deviceInfo.user = Build.VERSION.CODENAME;
    }
    
    return deviceInfo;
  }
  
  /* TODO(Wenjie): Implements this using the location service.*/
  public static Location getLocation() {
    return PhoneUtils.getPhoneUtils().getLocation();
  }
  
  private static ConnectivityManager getConnManager() {      
    return (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
  }
  
  private static String getCellularIp() {
    String ipAddress = null;
   
    try {
      for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); 
          en.hasMoreElements();) {
        NetworkInterface intf = en.nextElement();
        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); 
            enumIpAddr.hasMoreElements();) {
          InetAddress inetAddress = enumIpAddr.nextElement();
          if (!inetAddress.isLoopbackAddress()
              && inetAddress.getHostAddress().compareTo("0.0.0.0") != 0) {
            return inetAddress.getHostAddress().toString();
          }
        }
      }
    } catch (SocketException ex) {
      return null;
    }
    return null;
  }

  /* TODO(Wenjie): It assume that WifiInfo.getIpAddress() always returns an integer in
   * big endian. Otherwise, the order of the numbers in the IP String will need 
   * to be reversed.*/
  private static String intToIp(int number) {
    byte[] bytes = new byte[4];
    for (int i = 0; i < bytes.length; i++) {
      bytes[i] = (byte) ((number >> (32 - (4 - i) * 8)) & 0xFF);
    }
    try {
       /* 
       * Integer is encoded in network byte order (big endian), and getByAddress(byte[])
       * address that endian issue internally.
       */
      InetAddress ipAddress = InetAddress.getByAddress(bytes);
      return ipAddress.getHostAddress();
    } catch (UnknownHostException e) {
      Log.e(SpeedometerApp.TAG, "error when translating the wifi address to string");
      return null;
    }
  }
  
  private static String getWifiIp() {
    WifiManager wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    if (wifiInfo != null) {
      int ipAddress = wifiInfo.getIpAddress();
      return ipAddress == 0 ? null : intToIp(ipAddress);
    } else {
      return null;
    }
  }
  
  /* Wifi and 3G can be both active. We first see if wifi is active and return the wifi IP using
   * the WifiManager. Otherwise, we search an active network interface and return it as the 3G
   * network IP*/
  private static String getIp() {
    String ipStr = getWifiIp();
    if (ipStr == null) {
      ipStr = getCellularIp();
    }
    if (ipStr == null) {
      return "";
    } else {
      return ipStr;
    }
  }
  
  /** Returns the DeviceProperty needed to report the measurement result */
  public static DeviceProperty getDeviceProperty() {
    TelephonyManager manager =
        (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
    String carrierName = manager.getNetworkOperatorName();
    Location location = getLocation();
    ConnectivityManager connMan = getConnManager();
    NetworkInfo activeNetwork = connMan.getActiveNetworkInfo();
    String networkType = PhoneUtils.getPhoneUtils().getNetwork();
    String activeIp = getIp();
    if (activeNetwork != null && activeIp.compareTo("") == 0) {
      networkType = activeNetwork.getTypeName();
    }
    String versionName = PhoneUtils.getPhoneUtils().getAppVersionName();

    return new DeviceProperty(getDeviceInfo().deviceId, versionName,
        Calendar.getInstance().getTime(), getVersionStr(), activeIp, location.getLongitude(),
        location.getLatitude(), location.getProvider(), networkType, carrierName, 
        PhoneUtils.getPhoneUtils().getCurrentBatteryLevel(),
        PhoneUtils.getPhoneUtils().isCharging());
  }
}

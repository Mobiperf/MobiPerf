/* Copyright 2012 Mobiperf.
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
package com.mobiperf.mobiperf;

import com.mobiperf.speedometer.Config;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.ConnectivityManager;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

// TODO(Junxian): manage GPS information here, disallow GPS access from other
// parts, too messy
public class InformationCenter {
  // holds the threegtest that extends Activity
  public static Context activity;

  // WIFI or MOBIlE variables
  private static ConnectivityManager connectivityManager;

  // Mostly MOBILE variables
  private static TelephonyManager telephonyManager;
  private static MyPhoneStateListener phoneStateListener;

  private static CellLocation cellLocation;

  private static String networkOperatorName;
  private static String runId; // global runid
  private static String deviceId;
  private static String prefix;
  private static int signalStrength;
  private static int signalEcIo;
  private static boolean networkStatus;

  /**
   * Only called in threegtest.java onStart
   * 
   * @param a
   */
  public static void init(Context context) {
    activity = context;

    connectivityManager = (ConnectivityManager) activity
        .getSystemService(Context.CONNECTIVITY_SERVICE);
    telephonyManager = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
    phoneStateListener = new MyPhoneStateListener();

    reset();
  }

  /**
   * Called when a new run is needed. Refresh all information
   */
  public static void reset() {
    networkOperatorName = null;
    runId = null;
    deviceId = null;
    prefix = null;
    signalStrength = -1;
    signalEcIo = -1;
    networkStatus = false;

    cellLocation = telephonyManager.getCellLocation();

    // unregister listener first, otherwise, callback will call null
    // function
    if (telephonyManager != null && phoneStateListener != null) {
      telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }
    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
  }

  /**
   * Use this to prevent NullPointerException
   */
  public static TelephonyManager getTelephoneMamager() {
    if (telephonyManager == null) {
      telephonyManager = (TelephonyManager) activity.getSystemService(Context.TELEPHONY_SERVICE);
    }
    return telephonyManager;

  }

  /**
   * Use this to prevent NullPointerException
   */
  public static ConnectivityManager getConnectivityMamager() {
    if (connectivityManager == null) {
      connectivityManager = (ConnectivityManager) activity
          .getSystemService(Context.CONNECTIVITY_SERVICE);
    }
    return connectivityManager;
  }

  public static String getRunId() {
    if (runId == null) { // initiate for the first time
      runId = ("" + System.currentTimeMillis()).substring(0, 10); // runID
      // in
      // seconds
    }
    return runId;
  }

  public static String getDeviceID() {
    if (deviceId == null) {
      deviceId = getTelephoneMamager().getDeviceId();
    }
    return deviceId;
  }

  public static String getPrefix() {
    if (prefix == null) {
      prefix = "<" + Config.TYPE + "><" + getDeviceID() + "><" + getRunId() + ">";
    }
    return prefix;
  }

  /**
   * 
   * @return true if currently connected and false otherwise
   */
  public static boolean isConnected() {
    try {
      return getConnectivityMamager().getActiveNetworkInfo().isConnected();
    } catch (NullPointerException npe) {
      return false;
    }
  }

  // returns cell id
  public static int getCellId() {
    if (cellLocation == null) {
      return 65535;
    }
    if (cellLocation instanceof GsmCellLocation) {
      GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
      int cid = gsmCellLocation.getCid();
      return cid & 0xffff;
    } else
      return 65535;

  }

  public static int getLAC() {
    if (cellLocation == null) {
      return 65535;
    }
    if (cellLocation instanceof GsmCellLocation) {
      GsmCellLocation gsmCellLocation = (GsmCellLocation) cellLocation;
      int lac = gsmCellLocation.getLac();
      return lac & 0xffff;
    } else
      return 65535;
  }

  /**
   * @author Junxian Huang Called by Service_Thread / Service_Thread_Small [0] NETWORK:<Type:xx>
   *         WiFi, WiMax or if MOBILE should be MOBILE.EVDO_A etc
   * 
   *         int TYPE_MOBILE The Default Mobile data connection. int TYPE_MOBILE_DUN A DUN-specific
   *         Mobile data connection. int TYPE_MOBILE_HIPRI A High Priority Mobile data connection.
   *         int TYPE_MOBILE_MMS An MMS-specific Mobile data connection. int TYPE_MOBILE_SUPL A
   *         SUPL-specific Mobile data connection. int TYPE_WIFI The Default WIFI data connection.
   *         int TYPE_WIMAX
   * 
   * 
   * 
   *         [1] NETWORK:<TypeID:xx> networkTypeID * 1000 + mobileNetworkTypeID (for mobile,
   *         networkTypeID(which is TYPE_MOBILE) = 0) \n\n if error happens, [1] == -1000, [0] == ""
   *         if wifi, [1] == TYPE_WIFI * 1000, [0] == "WiFi" if mobile, [1] == MOBILE_TYPE_ID, [0]
   *         == "MOBILE.EVDO"
   **/
  public static String[] getTypeNameAndId() {
    String tempNetworkTypeName = getNetworkTypeName();
    int tempNetworkTypeID = getNetworkTypeID() * 1000;

    if (tempNetworkTypeID == ConnectivityManager.TYPE_MOBILE) {
      int id = getMobileNetworkTypeID();
      tempNetworkTypeID += id;
      tempNetworkTypeName += "." + getMobileNetworkTypeName(id);
    }
    return new String[] { tempNetworkTypeName, "" + tempNetworkTypeID };
  }

  // returns an integer indicating the current network type
  // returns -1 if not connected
  public static int getNetworkTypeID() {
    int networkTypeID;
    if (isConnected()) {
      networkTypeID = getConnectivityMamager().getActiveNetworkInfo().getType();
    } else {
      networkTypeID = -1;
    }
    return networkTypeID;
  }

  // Returns network type name String ("WIFI" OR "MOBILE" OR "mobile", or
  // other values)
  // if not connected, return ""
  public static String getNetworkTypeName() {
    // System.out.println("inside getNetworkTypeName " + networkTypeName);
    String networkTypeName = null;
    if (isConnected()) {
      networkTypeName = getConnectivityMamager().getActiveNetworkInfo().getTypeName();
    } else {
      networkTypeName = "";
    }
    return networkTypeName;
  }

  // returns int containing the mobile network ID
  // returns -1 if not connected or no mobile connection
  public static int getMobileNetworkTypeID() {
    int mobileNetworkTypeID;
    int networkTypeID = getNetworkTypeID();
    if (isConnected() && networkTypeID == ConnectivityManager.TYPE_MOBILE)
      mobileNetworkTypeID = getTelephoneMamager().getNetworkType();
    else
      mobileNetworkTypeID = -1;
    return mobileNetworkTypeID;
  }

  // return human readable name of a network id
  public static String getMobileNetworkTypeName(int id) {
    String mobileNetworkTypeName;
    switch (id) {
    case TelephonyManager.NETWORK_TYPE_UNKNOWN:
      mobileNetworkTypeName = "UNKNOWN";
      break;
    case TelephonyManager.NETWORK_TYPE_GPRS:
      mobileNetworkTypeName = "GPRS";
      break;
    case TelephonyManager.NETWORK_TYPE_EDGE:
      mobileNetworkTypeName = "EDGE";
      break;
    case TelephonyManager.NETWORK_TYPE_UMTS:
      mobileNetworkTypeName = "UMTS";
      break;
    case 4:
      mobileNetworkTypeName = "CDMA";// Current network is CDMA: Either
      // IS95A or IS95B
      break;
    case 5:
      mobileNetworkTypeName = "EVDO_0";// Current network is EVDO revision
      // 0
      break;
    case 6:
      mobileNetworkTypeName = "EVDO_A";// Current network is EVDO revision
      // A
      break;
    case 7:
      mobileNetworkTypeName = "1xRTT";
      break;
    case 8:
      mobileNetworkTypeName = "HSDPA";
      break;
    case 9:
      mobileNetworkTypeName = "HSUPA";
      break;
    case 10:
      mobileNetworkTypeName = "HSPA";
      break;
    case 11:
      mobileNetworkTypeName = "IDEN";
      // public static final int NETWORK_TYPE_IDEN
      // Since: API Level 8
      // Current network is iDen
      // Constant Value: 11 (0x0000000b)
      break;

    case 12:
      mobileNetworkTypeName = "EVDO_B";
      // public static final int NETWORK_TYPE_EVDO_B
      // Since: API Level 9
      // Current network is EVDO revision B
      // Constant Value: 12 (0x0000000c)
      break;

    case 13:
      mobileNetworkTypeName = "LTE";
      // public static final int NETWORK_TYPE_LTE
      // Since: API Level 11
      // Current network is LTE
      // Constant Value: 13 (0x0000000d)
      break;

    case 14:
      mobileNetworkTypeName = "EHRPD";
      // public static final int NETWORK_TYPE_EHRPD
      // Since: API Level 11
      // Current network is eHRPD
      // Constant Value: 14 (0x0000000e)
      break;

    default:
      mobileNetworkTypeName = "" + id;
      // mobileNetworkTypeName = "MOBILE";
    }

    return mobileNetworkTypeName;
  }

  // returns carrier type
  public static String getNetworkOperator() {
    if (networkOperatorName == null) {
      networkOperatorName = getTelephoneMamager().getNetworkOperatorName();
    }
    return networkOperatorName;
  }

  public static boolean getNetworkStatus() {
    return networkStatus;
  }

  public static void setNetworkStatus(boolean status) {
    networkStatus = status;
  }

  public static int getSignalStrength() {
    long startTime = System.currentTimeMillis();
    while (signalStrength == -1) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      long endTime = System.currentTimeMillis();
      if (endTime - startTime > 10000)
        break;
    }
    return signalStrength;
  }

  public static int getSignalEcIo() {
    long startTime = System.currentTimeMillis();
    while (signalEcIo == -1) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      long endTime = System.currentTimeMillis();
      if (endTime - startTime > 10000)
        break;
    }
    return signalEcIo;
  }

  // determines if currently connected to the internet
  // if user is not connected, he/she is asked to connect to the internet via
  // an alert
  // dialog box and the app closes

  private static class MyPhoneStateListener extends PhoneStateListener {
    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) { // Since:
      // API
      // Level
      // 7
      InformationCenter.signalStrength = signalStrength.getCdmaDbm();
      InformationCenter.signalEcIo = signalStrength.getCdmaEcio();
      System.out.println("SignalChange CDMADBM:" + signalStrength.getCdmaDbm() + " CDMAECIO:"
          + signalStrength.getCdmaEcio() + " EVDODBM:" + signalStrength.getEvdoDbm() + " EVDOECIO:"
          + signalStrength.getEvdoEcio() + " EVDOSNR:" + signalStrength.getEvdoSnr() + " GSMber:"
          + signalStrength.getGsmBitErrorRate() + " GSMss:" + signalStrength.getGsmSignalStrength()
          + " ");

      // getTelephoneMamager().listen(phoneStateListener,
      // PhoneStateListener.LISTEN_NONE);
    }
  };

  /**
   * 
   * @return -1 on error, version code on success
   */
  public static String getPackageVersionCode() {
    PackageManager manager = activity.getPackageManager();
    try {
      PackageInfo info = manager.getPackageInfo(activity.getPackageName(), 0);
      return "" + info.versionCode;
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    }
    return "-1";
  }

  /**
   * 
   * @return -1 on error, version name on success
   */
  public static String getPackageVersionName() {
    PackageManager manager = activity.getPackageManager();
    try {
      PackageInfo info = manager.getPackageInfo(activity.getPackageName(), 0);
      return info.versionName;
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    }
    return "-1";
  }

}

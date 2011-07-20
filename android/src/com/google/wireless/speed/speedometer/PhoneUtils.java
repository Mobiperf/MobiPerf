/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.wireless.speed.speedometer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.WebView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


/**
 * Phone related utilities.
 *
 * @author klm@google.com (Michael Klepikov)
 */
public class PhoneUtils {

  private static final String DEBUG_TAG = "PhoneUtils";
  private static final String ANDROID_STRING = "Android";
  /** Returned by {@link #getNetwork()}. */
  public static final String NETWORK_WIFI = "Wifi";

  /**
   * The app that uses this class. The app must remain alive for longer than
   * PhoneUtils objects are in use.
   *
   * @see #setGlobalContext(Context)
   */
  private static Context globalContext = null;

  /** A singleton instance of PhoneUtils. */
  private static PhoneUtils singletonPhoneUtils = null;

  /** Phone context object giving access to various phone parameters. */
  private Context context = null;

  /** Allows to obtain the phone's location, to determine the country. */
  private LocationManager locationManager = null;

  /** The name of the location provider with "coarse" precision (cell/wifi). */
  private String locationProviderName = null;

  /** Allows to disable going to low-power mode where WiFi gets turned off. */
  WakeLock wakeLock = null;

  /** Call initNetworkManager() before using this var. */
  private ConnectivityManager connectivityManager = null;

  /** Call initNetworkManager() before using this var. */
  private TelephonyManager telephonyManager = null;


  protected PhoneUtils(Context context) {
    this.context = context;
  }

  /**
   * The owner app class must call this method from its onCreate(), before
   * getPhoneUtils().
   */
  public static synchronized void setGlobalContext(Context newGlobalContext) {
    assert newGlobalContext != null;
    assert singletonPhoneUtils == null;  // Should not yet be created
    // Not supposed to change the owner app
    assert globalContext == null || globalContext == newGlobalContext;

    globalContext = newGlobalContext;
  }

  public static synchronized void releaseGlobalContext() {
    globalContext = null;
    singletonPhoneUtils = null;
  }

  /** Returns the context previously set with {@link #setGlobalContext}. */
  public static synchronized Context getGlobalContext() {
    assert globalContext != null;
    return globalContext;
  }

  /**
   * Returns a singleton instance of PhoneUtils. The caller must call
   * {@link #setGlobalContext(Context)} before calling this method.
   */
  public static synchronized PhoneUtils getPhoneUtils() {
    if (singletonPhoneUtils == null) {
      assert globalContext != null;
      singletonPhoneUtils = new PhoneUtils(globalContext);
    }

    return singletonPhoneUtils;
  }

  /**
   * Returns a string representing this phone:
   *
   * "Android_<hardware-type>-<build-release>_<network-type>_" +
   * "<network-carrier>_<mobile-type>_<Portrait-or-Landscape>"
   *
   * hardware-type is e.g. "dream", "passion", "emulator", etc.
   * build-release is the SDK public release number e.g. "2.0.1" for Eclair.
   * network-type is e.g. "Wifi", "Edge", "UMTS", "3G".
   * network-carrier is the mobile carrier name if connected via the SIM card,
   *     or the Wi-Fi SSID if connected via the Wi-Fi.
   * mobile-type is the phone's mobile network connection type -- "GSM" or "CDMA".
   *
   * If the device screen is currently in lanscape mode, "_Landscape" is
   * appended at the end.
   *
   * TODO(klm): This needs to be converted into named URL args from positional,
   * both here and in the iPhone app. Otherwise it's hard to add extensions,
   * especially if there is optional stuff like
   *
   * @return a string representing this phone
   */
  public String generatePhoneId() {
    String device = Build.DEVICE.equals("generic") ? "emulator" : Build.DEVICE;
    String network = getNetwork();
    String carrier = (network == NETWORK_WIFI) ?
        getWifiCarrierName() : getTelephonyCarrierName();

    StringBuilder stringBuilder = new StringBuilder(ANDROID_STRING);
    stringBuilder.append('-').append(device).append('_')
        .append(Build.VERSION.RELEASE).append('_').append(network)
        .append('_').append(carrier).append('_').append(getTelephonyPhoneType())
        .append('_').append(isLandscape() ? "Landscape" : "Portrait");

    return stringBuilder.toString();
  }

  /**
   * Lazily initializes the network managers.
   *
   * As a side effect, assigns connectivityManager and telephonyManager.
   */
  private synchronized void initNetwork() {
    if (connectivityManager == null) {
      ConnectivityManager tryConnectivityManager =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      TelephonyManager tryTelephonyManager =
          (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

      // Assign to member vars only after all the get calls succeeded,
      // so that either all get assigned, or none get assigned.
      connectivityManager = tryConnectivityManager;
      telephonyManager = tryTelephonyManager;

      // Some interesting info to look at in the logs
      NetworkInfo[] infos = connectivityManager.getAllNetworkInfo();
      for (NetworkInfo networkInfo : infos) {
        Log.i(DEBUG_TAG, "Network: " + networkInfo);
      }
      Log.i(DEBUG_TAG, "Phone type: " + getTelephonyPhoneType() +
            ", Carrier: " + getTelephonyCarrierName());
    }
    assert connectivityManager != null;
    assert telephonyManager != null;
  }

  /** Returns the network that the phone is on (e.g. Wifi, Edge, GPRS, etc). */
  public String getNetwork() {
    initNetwork();
    NetworkInfo networkInfo =
      connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
    if (networkInfo != null &&
        networkInfo.getState() == NetworkInfo.State.CONNECTED) {
      return NETWORK_WIFI;
    } else {
      return getTelephonyNetworkType();
    }
  }

  private static final String[] NETWORK_TYPES = {
    "UNKNOWN",  // 0  - NETWORK_TYPE_UNKNOWN
    "GPRS",     // 1  - NETWORK_TYPE_GPRS
    "EDGE",     // 2  - NETWORK_TYPE_EDGE
    "UMTS",     // 3  - NETWORK_TYPE_UMTS
    "CDMA",     // 4  - NETWORK_TYPE_CDMA
    "EVDO_0",   // 5  - NETWORK_TYPE_EVDO_0
    "EVDO_A",   // 6  - NETWORK_TYPE_EVDO_A
    "1xRTT",    // 7  - NETWORK_TYPE_1xRTT
    "HSDPA",    // 8  - NETWORK_TYPE_HSDPA
    "HSUPA",    // 9  - NETWORK_TYPE_HSUPA
    "HSPA",     // 10 - NETWORK_TYPE_HSPA
    "IDEN",     // 11 - NETWORK_TYPE_IDEN
    "EVDO_B",   // 12 - NETWORK_TYPE_EVDO_B
    "LTE",      // 13 - NETWORK_TYPE_LTE
    "EHRPD",    // 14 - NETWORK_TYPE_EHRPD
  };

  /** Returns mobile data network connection type. */
  private String getTelephonyNetworkType() {
    assert NETWORK_TYPES[14] == "EHRPD";

    int networkType = telephonyManager.getNetworkType();
    if (networkType < NETWORK_TYPES.length) {
      return NETWORK_TYPES[telephonyManager.getNetworkType()];
    } else {
      return "Unrecognized: " + networkType;
    }
  }

  /** Returns "GSM", "CDMA". */
  private String getTelephonyPhoneType() {
    switch (telephonyManager.getPhoneType()) {
      case TelephonyManager.PHONE_TYPE_CDMA:
        return "CDMA";
      case TelephonyManager.PHONE_TYPE_GSM:
        return "GSM";
      case TelephonyManager.PHONE_TYPE_NONE:
        return "None";
    }
    return "Unknown";
  }

  /** Returns current mobile phone carrier name, or empty if not connected. */
  private String getTelephonyCarrierName() {
    return telephonyManager.getNetworkOperatorName();
  }

  /** Returns current Wi-Fi SSID, or null if Wi-Fi is not connected. */
  private String getWifiCarrierName() {
    WifiManager wifiManager =
        (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    if (wifiInfo != null) {
      return wifiInfo.getSSID();
    }
    return null;
  }

  /**
   * Lazily initializes the location manager.
   *
   * As a side effect, assigns locationManager and locationProviderName.
   */
  private synchronized void initLocation() {
    if (locationManager == null) {
      LocationManager manager =
        (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

      Criteria criteriaCoarse = new Criteria();
      /* "Coarse" accuracy means "no need to use GPS".
       * Typically a gShots phone would be located in a building,
       * and GPS may not be able to acquire a location.
       * We only care about the location to determine the country,
       * so we don't need a super accurate location, cell/wifi is good enough.
       */
      criteriaCoarse.setAccuracy(Criteria.ACCURACY_COARSE);
      criteriaCoarse.setPowerRequirement(Criteria.POWER_LOW);
      String providerName =
          manager.getBestProvider(criteriaCoarse, /*enabledOnly=*/true);
      List<String> providers = manager.getAllProviders();
      for (String providerNameIter : providers) {
        LocationProvider provider = manager.getProvider(providerNameIter);
        Log.i(DEBUG_TAG, providerNameIter + ": " +
              (manager.isProviderEnabled(providerNameIter) ? "enabled" : "disabled"));
      }

      /* Make sure the provider updates its location.
       * Without this, we may get a very old location, even a
       * device powercycle may not update it.
       * {@see android.location.LocationManager.getLastKnownLocation}.
       */
      manager.requestLocationUpdates(providerName,
                                     /*minTime=*/0,
                                     /*minDistance=*/0,
                                     new LoggingLocationListener(),
                                     Looper.getMainLooper());
      locationManager = manager;
      locationProviderName = providerName;
    }
    assert locationManager != null;
    assert locationProviderName != null;
  }

  /**
   * Returns the location of the device.
   *
   * @return the location of the device
   */
  public Location getLocation() {
    initLocation();
    Location location = locationManager.getLastKnownLocation(locationProviderName);
    if (location == null) {
      Log.e(DEBUG_TAG,
            "Cannot obtain location from provider " + locationProviderName);
    }
    return location;
  }

  /** Prevents the phone from going to low-power mode where WiFi turns off. */
  public synchronized void acquireWakeLock() {
    if (wakeLock == null) {
      PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
      wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "tag");
    }
    wakeLock.acquire();
  }

  /** Should be called on application shutdown. Releases global resources. */
  public synchronized void shutDown() {
    if (wakeLock != null) {
      wakeLock.release();
    }
  }

  /**
   * Returns true if the phone is in landscape mode.
   */
  public boolean isLandscape() {
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    return display.getWidth() > display.getHeight();
  }

  /**
   * Captures a screenshot of a WebView, except scrollbars, and returns it as a
   * Bitmap.
   *
   * @param webView The WebView to screenshot.
   * @return A Bitmap with the screenshot.
   */
  public static Bitmap captureScreenshot(WebView webView) {
    Picture picture = webView.capturePicture();
    int width = Math.min(picture.getWidth(),
        webView.getWidth() - webView.getVerticalScrollbarWidth());
    int height = Math.min(picture.getHeight(), webView.getHeight());
    Bitmap bitmap = Bitmap.createBitmap(width, height, Config.RGB_565);
    Canvas cv = new Canvas(bitmap);
    cv.drawPicture(picture);
    return bitmap;
  }

  /**
   * A dummy listener that just logs callbacks.
   */
  private static class LoggingLocationListener implements LocationListener {

    @Override
    public void onLocationChanged(Location location) {
      Log.d(DEBUG_TAG, "location changed");
    }

    @Override
    public void onProviderDisabled(String provider) {
      Log.d(DEBUG_TAG, "provider disabled: " + provider);
    }

    @Override
    public void onProviderEnabled(String provider) {
      Log.d(DEBUG_TAG, "provider enabled: " + provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
      Log.d(DEBUG_TAG, "status changed: " + provider + "=" + status);
    }
  }

  /**
   * Starts a command, redirecting its output to given streams.
   * Returns a ProcessRunner object to track and control execution.
   *
   * @param command The command to launch.
   * @param outStream Where to redirect stdout of the command.
   * @param errStream Where to redirect stderr of the command.
   * @return A {@link ProcessRunner} object for the running command.
   * @throws IOException If there is an error launching the command.
   */
  public ProcessRunner startProcessAsRoot(List<String> command,
                                    OutputStream outStream,
                                    OutputStream errStream)
      throws IOException {
    ProcessRunner runner = new ProcessRunner(command);
    runner.start(outStream, errStream);
    return runner;
  }


  /** Runs a command and returns a list of stdout+stderr lines. */
  public List<String> runCommandGetOutput(String cmd)
      throws IOException {
    return runCommandGetOutput(Arrays.asList(cmd));
  }

  /** Runs a command and returns a list of stdout+stderr lines. */
  public List<String> runCommandGetOutput(List<String> cmd)
      throws IOException {
    ProcessRunner tmpRunner = new ProcessRunner(cmd);
    return tmpRunner.runCommandGetOutput(/*requireZeroExit=*/true);
  }

  /**
   * Types of interfaces to return from {@link #getUpInterfaces(InterfaceType)}.
   */
  public enum InterfaceType {
    /** Local and external interfaces. */
    ALL,

    /** Only external interfaces. */
    EXTERNAL_ONLY,
  }

  /**
   * Returns a list of active ("up") network interface names.
   *
   * @param ifType  what interfaces to return -- see {@link InterfaceType}.
   * @return a list of active network interface names.
   */
  public List<String> getUpInterfaces(InterfaceType ifType) throws IOException {
    List<String> netcfgOut = runCommandGetOutput("netcfg");
    List<String> upIfs = new ArrayList<String>();
    for (String outLine : netcfgOut) {
      String[] splitLine = outLine.split(" +", 3);
      String linkDirection = splitLine[1];
      if (splitLine.length != 3 ||
          !(linkDirection.equals("UP") || linkDirection.equals("DOWN"))) {
        throw new IOException("Bad netcfg output: " + outLine);
      }
      // 1) Must be up
      // 2) If externalOnly, the name must not be "lo" or start with "dummy".
      String interfaceName = splitLine[0];
      if (splitLine[1].equals("UP") &&
          !(ifType == InterfaceType.EXTERNAL_ONLY &&
            (interfaceName.equals("lo") || interfaceName.startsWith("dummy")))) {
          upIfs.add(splitLine[0]);
      }
    }

    if (upIfs.size() > 1) {
      for (String wifiInterface: new String[] {"eth0", "wlan0"}) {
        if (upIfs.contains(wifiInterface)) {
          // If both wifi and mobile-wireless interfaces exist, then the wifi one must be
          // inactive (otherwise the phone would have turned off the mobile-wireless interface),
          // so, remove the inactive wifi interface here.
          // Note that if the device currently uses wifi actively, then mobile-wireless interface
          // should be off and upIfs.size() would be 1, so, we won't reach here in that case.
          upIfs.remove(wifiInterface);
        }
      }
    }

    return upIfs;
  }

  /** Returns a debug printable representation of a string list. */
  public static String debugString(List<String> stringList) {
    StringBuilder result = new StringBuilder("[");
    Iterator<String> listIter = stringList.iterator();
    if (listIter.hasNext()) {
      result.append('"');  // Opening quote for the first string
      result.append(listIter.next());
      while (listIter.hasNext()) {
        result.append("\", \"");
        result.append(listIter.next());
      }
      result.append('"');  // Closing quote for the last string
    }
    result.append(']');
    return result.toString();
  }

  /** Returns a debug printable representation of a string array. */
  public static String debugString(String[] arr) {
    return debugString(Arrays.asList(arr));
  }

  /** Returns a list of PIDs of processes with a given name. */
  public List<String> getPids(String processName) throws IOException {
    List<String> pids = new ArrayList<String>();
    List<String> psOut = runCommandGetOutput("ps");
    if (psOut.size() < 2) {
      throw new IOException("Bad ps output: " + debugString(psOut));
    }
    String[] headers = psOut.get(0).split(" +");
    if (headers.length != 8) {
      throw new IOException("Number of ps headers is not 8: " +
          debugString(headers));
    } else if (!headers[1].equals("PID")) {
      throw new IOException("Unexpected PID header (second item): " +
          debugString(headers));
    } else if (!headers[headers.length - 1].equals("NAME")) {
      throw new IOException("Unexpected NAME header (last item): " +
          debugString(headers));
    }
    Iterator<String> psLineIter = psOut.listIterator(1);  // From line #2
    while (psLineIter.hasNext()) {
      String psLine = psLineIter.next();
      // Format: USER PID PPID VSIZE RSS WCHAN PC status NAME
      String[] psItems = psLine.split(" +", 9);
      if (psItems.length != 9) {
        throw new IOException("Unexpected ps output: " +
            debugString(psItems));
      }
      if (psItems[8].equals(processName)) {
        pids.add(psItems[1]);
      }
    }
    return pids;
  }

}

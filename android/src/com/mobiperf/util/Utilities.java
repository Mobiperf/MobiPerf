/* Copyright 2012 University of Michigan.
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
package com.mobiperf.util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.mobiperf.mobiperf.InformationCenter;
import com.mobiperf.speedometer.Config;

/**
 * @author hjx@umich.edu (Junxian Huang) TODO to be merged with Util.java Miscellaneous functions.
 */
public class Utilities {

  public static String genRandomString(int len) {
    StringBuilder sb = new StringBuilder("");
    Random ran = new Random();
    for (int i = 1; i <= len; i++) {
      sb.append((char) ('a' + ran.nextInt(26)));
    }
    return sb.toString();
  }

  public static double getMax(double[] a) {
    if (a == null || a.length == 0) {
      System.err.println("getMax invalid array");
      return Double.MIN_VALUE;
    }
    double max = Double.MIN_VALUE;
    for (int i = 0; i < a.length; i++) {
      if (a[i] > max) {
        max = a[i];
      }
    }
    return max;
  }

  public static double getMin(double[] a) {
    if (a == null || a.length == 0) {
      System.err.println("getMax invalid array");
      return Double.MIN_VALUE;
    }
    double min = Double.MAX_VALUE;
    for (int i = 0; i < a.length; i++) {
      if (a[i] < min) {
        min = a[i];
      }
    }
    return min;
  }

  public static double getMedian(double[] a) {
    if (a == null || a.length == 0) {
      System.err.println("getMedian invalid array");
      return Double.MIN_VALUE;
    }
    double[] tmp = a.clone();
    double median = 0;
    Arrays.sort(tmp);
    int len = tmp.length;
    if (len % 2 == 0) {
      // len is even, e.g., len = 4, => (2 + 1) / 2
      median = (tmp[len / 2] + tmp[len / 2 - 1]) / 2;
    } else {
      // len is odd, e.g. len = 3, => 1
      median = tmp[(len - 1) / 2];
    }
    return median;
  }

  public static double getAverage(double[] a) {
    if (a == null || a.length == 0) {
      System.err.println("getAverage invalid array");
      return Double.MIN_VALUE;
    }
    double total = 0;
    for (int i = 0; i < a.length; i++) {
      total += a[i];
    }
    return total / (double) a.length;
  }

  public static double getStandardDeviation(double[] a) {
    if (a == null || a.length == 0) {
      System.err.println("getStandardDeviation invalid array");
      return Double.MIN_VALUE;
    }
    double std = 0;
    double avg = Utilities.getAverage(a);
    for (int i = 0; i < a.length; i++) {
      std += (a[i] - avg) * (a[i] - avg);
    }
    return Math.sqrt(std / (double) (a.length - 1));
  }

  /**
   * make new array of results to increase it's size + 1 and add new res to the end
   * 
   * @param res
   */
  public static double[] pushResult(double[] results, double res) {
    double[] tmp = results.clone();
    results = new double[tmp.length + 1];
    for (int i = 0; i < tmp.length; i++)
      results[i] = tmp[i];
    results[tmp.length] = res;
    return results.clone();
  }

  public static String getAppPkg(Context c, int uid) throws NullPointerException {
    PackageManager pm = c.getPackageManager();
    String name = pm.getPackagesForUid(uid)[0];
    return name;
  }

  /**
   * @author Yunxing Dai get application info from android system
   */
  private static ApplicationInfo getAppInfo(Context c, String name) throws NameNotFoundException {
    PackageManager pm = c.getPackageManager();
    return pm.getApplicationInfo(name, PackageManager.GET_UNINSTALLED_PACKAGES);
  }

  /**
   * @author Yunxing Dai get application's icon based on uid given
   */
  public static Drawable getAppIcon(Context c, int uid) {
    PackageManager pm = c.getPackageManager();
    Drawable icon = null;
    try {

      String name = pm.getPackagesForUid(uid)[0];
      icon = (Drawable) getAppInfo(c, name).loadIcon(pm);
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NullPointerException e) {
      icon = null;
    }
    return icon;
  }

  /**
   * @author Yunxing Dai get application's user friendly label based on context and uid
   */
  public static String getAppLabel(Context c, int uid) {
    PackageManager pm = c.getPackageManager();
    String label = null;
    try {

      String name = pm.getPackagesForUid(uid)[0];
      label = (String) pm.getApplicationLabel(getAppInfo(c, name));
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NullPointerException e) {
      label = "unknown(uid=" + uid + ")";
    }
    return label;
  }

  /*
   * public static int setProgressStatus (int hardCodedValue) { int val = (int) (time() % 3) +
   * hardCodedValue - 1; if (val < 0) val = 0; if (val > 100) val = 100; return val; }
   */

  /**
   * @author Junxian Huang getAgo string
   * @param ts
   *          unix time in seconds
   */
  public static String getAgo(String ts) {
    long current = System.currentTimeMillis() / 1000;
    long old = Long.parseLong(ts);
    long ago = current - old;
    double min = (double) ago / 60.0;
    double hour = (double) min / 60.0;
    double day = (double) hour / 24.0;
    double month = (double) day / 30.0;
    double year = (double) day / 365.0;

    double min2 = (long) ((double) min * 10.0) / 10;
    double hour2 = (long) ((double) hour * 10.0) / 10;
    double day2 = (long) ((double) day * 10.0) / 10;
    double month2 = (long) ((double) month * 10.0) / 10;
    double year2 = (long) ((double) year * 10.0) / 10;

    String time_str = "";
    if (year >= 1) {
      time_str = "" + year2 + " years";
    } else if (month >= 1) {
      time_str = "" + month2 + " months";
    } else if (day >= 1) {
      time_str = "" + day2 + " days";
    } else if (hour >= 1) {
      time_str = "" + hour2 + " hours";
    } else if (min >= 1) {
      time_str = "" + min2 + " minutes";
    } else {
      time_str = "" + ago + " seconds";
    }
    return time_str + " ago";
  }

  /**
   * @author Junxian Huang Get previous run results list from server
   */
  public static String getPreviousResult(String runId) {
    String res = "";
    try {
      // Construct data
      String data = URLEncoder.encode("type", "UTF-8") + "="
          + URLEncoder.encode(Config.TYPE, "UTF-8");
      data += "&" + URLEncoder.encode("deviceId", "UTF-8") + "="
          + URLEncoder.encode(InformationCenter.getDeviceID(), "UTF-8");
      data += "&" + URLEncoder.encode("runId", "UTF-8") + "=" + URLEncoder.encode(runId, "UTF-8");

      // Send data
      URL url = new URL("http://mobiperf.com/php/getResult.php");
      URLConnection conn = url.openConnection();
      conn.setDoOutput(true);
      OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
      wr.write(data);
      wr.flush();

      // Get the response
      BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      String line;
      while ((line = rd.readLine()) != null) {
        // Process line...
        res += line;
      }
      wr.close();
      rd.close();
    } catch (Exception e) {
    }
    return res;
  }

  /**
   * @author Junxian Huang Get previous run results list from server
   */
  public static String getPreviousResultList() {
    String res = "";
    try {
      // Construct data
      String data = URLEncoder.encode("type", "UTF-8") + "="
          + URLEncoder.encode(Config.TYPE, "UTF-8");
      data += "&" + URLEncoder.encode("deviceId", "UTF-8") + "="
          + URLEncoder.encode(InformationCenter.getDeviceID(), "UTF-8");

      // Send data
      URL url = new URL("http://mobiperf.com/php/getList.php");
      URLConnection conn = url.openConnection();
      conn.setDoOutput(true);
      OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
      wr.write(data);
      wr.flush();

      // Get the response
      BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      String line;
      while ((line = rd.readLine()) != null) {
        // Process line...
        res += line;
      }
      wr.close();
      rd.close();
    } catch (Exception e) {
    }
    return res;
  }

  /**
   * @author Junxian Huang Send commands to server to results to database
   */
  public static void letServerWriteOutputToMysql() {
    try {
      // Construct data
      String data = URLEncoder.encode("type", "UTF-8") + "="
          + URLEncoder.encode(Config.TYPE, "UTF-8");
      data += "&" + URLEncoder.encode("deviceId", "UTF-8") + "="
          + URLEncoder.encode(InformationCenter.getDeviceID(), "UTF-8");
      data += "&" + URLEncoder.encode("runId", "UTF-8") + "="
          + URLEncoder.encode(InformationCenter.getRunId(), "UTF-8");

      // Send data
      URL url = new URL("http://mobiperf.com/php/put.php");
      URLConnection conn = url.openConnection();
      conn.setDoOutput(true);
      OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
      wr.write(data);
      wr.flush();

      // Get the response
      BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
      String line;
      while ((line = rd.readLine()) != null) {
        // Process line...
        Log.v("MobiPerf", line);
      }
      wr.close();
      rd.close();
    } catch (Exception e) {
    }
  }

  public static boolean testIptablesAvailability(Context context) {
    boolean iptables_available = false;
    try {
      Process rootProcess = Runtime.getRuntime().exec("su");
      DataOutputStream os = new DataOutputStream(rootProcess.getOutputStream());
      File dir = context.getFilesDir();
      String cmd = "iptables -L > " + dir.getAbsolutePath() + "/iptables.test\n";
      os.writeBytes(cmd);

      FileInputStream fis = context.openFileInput("iptables.test");
      DataInputStream dis = new DataInputStream(fis);

      while (dis.available() > 0) {
        String line = dis.readLine();
        if (line.indexOf("Chain INPUT") >= 0) {
          iptables_available = true;
          break;
        }
      }

    } catch (IOException e) {
    }

    Log.v("LOG", "iptables available: " + iptables_available);
    return iptables_available;
  }

  @Deprecated
  /**
   * New MobiPerf only runs on normal phones, without using root
   */
  public static boolean checkRootPrivilege() {
    Process p;
    try {
      // Preform su to get root privledges
      p = Runtime.getRuntime().exec("su");
      // Attempt to write a file to a root-only
      DataOutputStream os = new DataOutputStream(p.getOutputStream());
      os.writeBytes("echo \"Do I have root?\" >/data/temporary.txt\n");
      // Close the terminal
      os.writeBytes("exit\n");
      os.flush();
      try {
        p.waitFor();
        if (p.exitValue() != 255) {
          // TODO(huangshu): Code to run on success
          Log.v("MobiPerf", "checkroot: root");
          return true;
        }
      } catch (InterruptedException e) {
        // TODO(huangshu): Code to run in interrupted exception
      }
    } catch (IOException e) {
      // TODO(huangshu): Code to run in input/output exception
    }
    return false;
  }

  static Thread binaryThread;

  // Copies src file to dst file.
  // If the dst file does not exist, it is created
  public static void copy(File src, File dst) throws IOException {
    InputStream in = new FileInputStream(src);
    OutputStream out = new FileOutputStream(dst);

    // Transfer bytes from in to out
    byte[] buf = new byte[1024];
    int len;
    while ((len = in.read(buf)) > 0) {
      out.write(buf, 0, len);
    }
    in.close();
    out.close();
  }

  public static String getCurrentGMTTime() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss");
    sdf.setTimeZone(TimeZone.getTimeZone("GMT:00"));
    return sdf.format(new Date()).toString();
  }

  public static String getCurrentLocalTime() {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd,HH:mm:ss");
    return sdf.format(new Date()).toString();
  }

  @Deprecated
  public static void writeToFile(String filename, int mode, String data, Context context) {
    FileOutputStream fo = null;
    OutputStreamWriter osw = null;
    try {
      fo = context.openFileOutput(filename, mode);
      osw = new OutputStreamWriter(fo);
      osw.write(data);
      osw.close();
      fo.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static String signalServers = "";

  public static boolean checkConnection() {
    boolean successful = false;
    for (int i = 0; i < 3; i++) {
      try {
        Socket remoteTCPSocket = new Socket();
        SocketAddress remoteAddr = new InetSocketAddress("www.google.com", 80);
        remoteTCPSocket.connect(remoteAddr, 8000);
        Log.v("LOG", "checkConnection to google seems successful");
        // Log.v("LOG", "sent report to promotion server");
        remoteTCPSocket.close();
        successful = true;
        break;
      } catch (IOException e) {
        Log.v("LOG", "checkConnection failed: cannot connect to www.google.com:80");
        e.printStackTrace();
        successful = false;
      }
    }
    return successful;
  }

  /**
   * 
   * @param cmd
   * @return output
   */
  public static String executeCmd(String cmd, boolean sudo) {
    try {

      Process p;
      if (!sudo)
        p = Runtime.getRuntime().exec(cmd);
      else {
        p = Runtime.getRuntime().exec(new String[] { "su", "-c", cmd });
      }
      BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String s;
      String res = "";
      while ((s = stdInput.readLine()) != null) {
        res += s + "\n";
      }
      p.destroy();
      return res;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return "";

  }

  @Deprecated
  @SuppressWarnings("unused")
  public static long pingS(String serverIP, int ttl, int tl, int sl_p, long timeout) {

    String line = null;
    boolean flag;
    long start, end;

    try {
      if (tl == 0) {
        try {
          start = System.currentTimeMillis();
          line = Utilities.executeCmd("ping -c 1 -t " + ttl + " " + serverIP, false);
        } catch (Exception e1) {
          e1.printStackTrace();
        }
      }

      if (tl == 1) {
        try {
          start = System.currentTimeMillis();
          line = Utilities.executeCmd("ping -c 1 " + serverIP, false);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();

    }
    Log.v("LOG", "FAILED in ping test!!!!!!!!!!!!!!!!");
    return -1;
  }

}
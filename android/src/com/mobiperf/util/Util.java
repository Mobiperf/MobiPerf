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
package com.mobiperf.util;

import com.mobiperf.Logger;
import com.mobiperf.MeasurementError;
import com.mobiperf.R;
import com.mobiperf.SpeedometerApp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for Speedometer that does not require runtime information
 */
public class Util {
    
  /**
   * Filter out values equal or beyond the bounds and then compute the average of valid data
   * @param vals the list of values to be filtered
   * @param lowerBound the lower bound of valid data
   * @param upperBound the upper bound of valid data
   * @return a list of filtered data within the specified bounds
   */
  public static ArrayList<Double> applyInnerBandFilter(ArrayList<Double> vals, double lowerBound, 
      double upperBound) throws InvalidParameterException {
    
    double rrtTotal = 0;
    int initResultLen = vals.size();
    if (initResultLen == 0) {
      // Return the original array if it is of zero length. 
      throw new InvalidParameterException("The array size passed in is zero"); 
    }
    
    double rrtAvg = rrtTotal / initResultLen;
    // we should filter out the outliers in the rrt result based on the average
    ArrayList<Double> finalRrtResults = new ArrayList<Double>();
    int finalResultCnt = 0;
    rrtTotal = 0;    
    for (double rrtVal : vals) {
      if (rrtVal <= upperBound && rrtVal >= lowerBound) {
        finalRrtResults.add(rrtVal);
      }
    }

    return finalRrtResults;
  }
  
  /**
   * Compute the sum of the values in list
   * @param vals the list of values to sum up
   * @return the sum of the values in the list
   */
  public static double getSum(ArrayList<Double> vals) {
    double sum = 0;
    for (double val : vals) {
      sum += val;      
    }
    return sum;
  }
  
  public static String constructCommand(Object... strings) throws InvalidParameterException {
    String finalCommand = "";
    int len = strings.length;
    if (len < 0) {
      throw new InvalidParameterException("0 arguments passed in for constructing command");
    }
    
    for (int i = 0; i < len - 1; i++) {
      finalCommand += (strings[i] + " ");
    }
    finalCommand += strings[len - 1];
    return finalCommand;
  }  
  
  /**
   * Prepare the internal User-Agent string for use. This requires a
   * {@link Context} to pull the package name and version number for this
   * application.
   */
  public static String prepareUserAgent(Context context) {
    try {
      // Read package name and version number from manifest
      PackageManager manager = context.getPackageManager();
      PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
      return context.getString(R.string.user_agent);

    } catch (NameNotFoundException e) {
      Logger.e("Couldn't find package information in PackageManager", e);
      return context.getString(R.string.default_user_agent);
    }
  }
  
  public static double getStandardDeviation(ArrayList<Double> values, double avg) {
    double total = 0;
    for (double val : values) {
      double dev = val - avg;
      total += (dev * dev);
    }
    if (total > 0) {
      return Math.sqrt(total / values.size());
    } else {
      return 0;
    }
  }
  
  public static String getTimeStringFromMicrosecond(long microsecond) {
    Date timestamp = new Date(microsecond / 1000);
    return timestamp.toString();
  }

  /**
   * Returns a String array that contains the ICMP sequence number and the round
   * trip time extracted from a ping output. The first array element is the
   * sequence number and the second element is the round trip time.
   * 
   * Returns a null object if either element cannot be found.
   */
  public static String[] extractInfoFromPingOutput(String outputLine) {
    try {
      Pattern pattern = Pattern.compile("icmp_seq=([0-9]+)\\s.* time=([0-9]+(\\.[0-9]+)?)");
      Matcher matcher = pattern.matcher(outputLine);
      matcher.find();
      
      return new String[] {matcher.group(1), matcher.group(2)};
    } catch (IllegalStateException e) {
      return null;
    }
  }
  
  /**
   * Returns an integer array that contains the number of ICMP requests sent and the number
   * of responses received. The first element is the requests sent and the second element
   * is the responses received.
   * 
   * Returns a null object if either element cannot be found.
   */
  public static int[] extractPacketLossInfoFromPingOutput(String outputLine) {    
    try {
      Pattern pattern = Pattern.compile("([0-9]+)\\spackets.*\\s([0-9]+)\\sreceived");
      Matcher matcher = pattern.matcher(outputLine);
      matcher.find();
      
      return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
    } catch (IllegalStateException e) {
      return null;
    } catch (NumberFormatException e) {
      return null;
    } catch (NullPointerException e) {    
      return null;
    }
  }
  
  /**
   * Return a list of system environment path 
   */
  public static String[] fetchEnvPaths() {
    String path = "";
    Map<String, String> env = System.getenv();
    if (env.containsKey("PATH")) {
      path = env.get("PATH");
    }
    return (path.contains(":")) ? path.split(":") : (new String[]{path});
  }

  /**
   * Determine the ping executable based on ip address byte length
   */
  public static String pingExecutableBasedOnIPType (int ipByteLen, Context context) {
    Process testPingProc = null;
    String[] progList = fetchEnvPaths();
    String pingExecutable = null;
    if (progList != null && progList.length != 0) {
      for (String pingLocation : progList) {
        try {
          if (ipByteLen == 4) {
            pingExecutable = pingLocation + "/" + 
                             context.getString(R.string.ping_executable);
          } else if (ipByteLen == 16) {
            pingExecutable = pingLocation + "/" + 
                             context.getString(R.string.ping6_executable);
          }
          testPingProc = Runtime.getRuntime().exec(pingExecutable);
        } catch (IOException e) {
          // reset the executable
          pingExecutable = null;
          // The ping command doesn't exist in that path, try another one
          continue;
        } finally {
          if (testPingProc != null)
            testPingProc.destroy();
        }
        break;
      }
    }
    return pingExecutable;
  }
}

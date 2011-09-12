// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer.util;

import com.google.wireless.speed.speedometer.R;
import com.google.wireless.speed.speedometer.SpeedometerApp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;

/**
 * Utility class for Speedometer that does not require runtime information
 * 
 * @author mdw@google.com (Matt Welsh)
 * @author wenjiezeng@google.com (Wenjie Zeng)
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
      Log.e(SpeedometerApp.TAG, "Couldn't find package information in PackageManager", e);
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
}

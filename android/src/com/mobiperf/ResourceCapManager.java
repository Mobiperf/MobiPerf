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

import com.mobiperf.measurements.DnsLookupTask;
import com.mobiperf.measurements.HttpTask;
import com.mobiperf.measurements.PingTask;
import com.mobiperf.measurements.RRCTask;
import com.mobiperf.measurements.TCPThroughputTask;
import com.mobiperf.measurements.TracerouteTask;
import com.mobiperf.measurements.UDPBurstTask;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;

import android.content.Context;
import android.content.Intent;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.Callable;

import org.json.JSONException;

/**
 * A basic power manager implementation that decides whether a measurement can be scheduled
 * based on the current battery level: no measurements will be scheduled if the current battery
 * is lower than a threshold.
 */
public class ResourceCapManager {

  public enum DataUsageProfile {
    PROFILE1, PROFILE2, PROFILE3, PROFILE4, UNLIMITED
  }

  /** The minimum threshold below which no measurements will be scheduled */
  private int minBatteryThreshold;
  private Context context = null;
  private int dataLimit;//in Byte
  private DataUsageProfile dataUsageProfile;

  // Constants for how much data can be consumed under each profile
  private static int UNLIMITED_LIMIT = -1;
  private static int PROFILE1_LIMIT = 50 * 1024 * 1024;
  private static int PROFILE2_LIMIT = 100 * 1024 * 1024;
  private static int PROFILE3_LIMIT = 250 * 1024 * 1024;
  private static int PROFILE4_LIMIT = 500 * 1024 * 1024;

  // Looking up various phone util data uses the network. 
  // It's hard to measure how much.
  // The good news is that this value is basically constant!
  public static int PHONEUTILCOST = 3 * 1024;

  public ResourceCapManager(int batteryThresh, Context context) {
    this.minBatteryThreshold = batteryThresh;
    this.dataLimit=PROFILE3_LIMIT;
    this.context=context;
    this.dataUsageProfile=DataUsageProfile.PROFILE3;		
  }

  /** 
   * Sets the minimum battery percentage below which measurements cannot be run.
   * 
   * @param batteryThresh the battery percentage threshold between 0 and 100
   */
  public synchronized void setBatteryThresh(int batteryThresh) throws IllegalArgumentException {
    if (batteryThresh < 0 || batteryThresh > 100) {
      throw new IllegalArgumentException("batteryCap must fall between 0 and 100, inclusive");
    }
    this.minBatteryThreshold = batteryThresh;
  }

  public synchronized int getBatteryThresh() {
    return this.minBatteryThreshold;
  }

  /**
   * Given a data profile string, set the data limit and profile code accordingly.
   * 
   * If an invalid code is given, leave as default (250 MB) and print a warning.
   * 
   * @param dataLimitStr String describing the profile
   */
  public synchronized void setDataUsageLimit(String dataLimitStr) {
    if (dataLimitStr.equals("50 MB")) {
      dataLimit = PROFILE1_LIMIT;
      dataUsageProfile = DataUsageProfile.PROFILE1;
    } else if (dataLimitStr.equals("100 MB")) {
      dataLimit = PROFILE2_LIMIT;
      dataUsageProfile = DataUsageProfile.PROFILE2;
    } else if (dataLimitStr.equals("250 MB")) {
      dataLimit = PROFILE3_LIMIT;
      dataUsageProfile = DataUsageProfile.PROFILE3;
    } else if (dataLimitStr.equals("500 MB")) {
      dataLimit = PROFILE4_LIMIT;
      dataUsageProfile = DataUsageProfile.PROFILE4;
    } else if (dataLimitStr.equals("Unlimited")) {
      dataLimit = UNLIMITED_LIMIT;
      dataUsageProfile = DataUsageProfile.UNLIMITED;
    } else {
      Logger.w("Specified limit " + dataLimitStr + " not found!");
    }
  }	

  /**
   * @return The current data limit in bytes.
   */
  public synchronized int getDataLimit() {
    return this.dataLimit;
  }

  /**
   * @return An enum representing the data usage limit.
   */
  public synchronized DataUsageProfile getDataUsageProfile(){
    return this.dataUsageProfile;
  }

  /**
   * Reset the data used in the data usage file to 0.
   * This should never be done unless the file does not exist.
   */
  private void resetDataUsage() {
    File file = new File(context.getFilesDir(), "datausage");
    if (file.exists()) {
      Logger.e("Attempting to overwrite a file that exists!!!!");
    }
    long usageStartTimeSec = (System.currentTimeMillis() / 1000);
    writeDataUsageToFile(0, usageStartTimeSec);
  }


  /**
   * Store the data used this period and the beginning of the period in a file,
   * in the format [time reset, in seconds]_[bytes used].
   * 
   * Note that the data used can be negative, due to a underused data budget
   * from last period.
   * 
   * @param dataUsed The updated amount of data to write
   * @param time The updated time to write
   */
  private synchronized void writeDataUsageToFile(long dataUsed, long time) {
    try {
      FileOutputStream outputStream =
          context.openFileOutput("datausage", Context.MODE_PRIVATE);
      String usageStat = time + "_" + dataUsed;
      outputStream.write(usageStat.getBytes());
      Logger.i("Updating data usage: " + dataUsed + " Byte used from "
          + time);
      outputStream.close();
    } catch (IOException e) {
      Logger.e("Error in creating data usage file");
      e.printStackTrace();
    }
  }

  /**
   * Read the usage data (start of usage period and quantity used in bytes)
   * from the usage data file.
   * 
   * @return An array consisting of the start of the usage period, then the data 
   * used so far.  If the file does not exist, returns -1 in each argument.
   */
  private synchronized long[] readUsageFromFile() {
    long[] retval = {-1, -1};
    File file = new File(context.getFilesDir(), "datausage");
    if (!file.exists()) {
      return retval;
    }
    try {
      String content = "";
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        content += line;
      }
      String[] toks = content.split("_");
      long usageStartTimeSec = Long.parseLong(toks[0]);
      long dataUsed = Long.parseLong(toks[1]);  

      retval[0] = usageStartTimeSec;
      retval[1] = dataUsed;

      br.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return retval;
  }    

  /**
   * Updates the data consumption period: using the current time move ahead to the
   * correct data consumption period, and also update the data used so far.
   * 
   * Data assigned to a previous data period is subtracted; this can go below zero,
   * effectively crediting unused data to future tasks.
   * 
   * @param dataUsed Data consumed since the start of the last period
   * @param usageStartTimeSec Time since the start of the last period
   * @return
   */
  private long setNewDataConsumptionPeriod(long dataUsed, long usageStartTimeSec) {
    long time_per_period = Config.DEFAULT_DATA_MONITOR_PERIOD_DAY * 24 * 60 * 60;

    Logger.i("Finished data consumption period that began at time:" + usageStartTimeSec + 
      " having " +dataUsed + " consumed");

    // Figure out how many periods have passed 
    int periods = (int) (((float) ((long) (System.currentTimeMillis() / 1000) - usageStartTimeSec))
        / (float) time_per_period);

    // Update usageStarTimeSec to the appropriate period
    usageStartTimeSec += periods * time_per_period;

    // Discount from the data used data that is budgeted to previous periods.
    // Note that this could go less than zero if we are below budget.
    long datalimit_per_period = (getDataLimit() * 
        Config.DEFAULT_DATA_MONITOR_PERIOD_DAY) / 30;
    dataUsed = dataUsed - (((int) (periods)) * datalimit_per_period);
    Logger.i("Net data usage at start of period: " + dataUsed);

    writeDataUsageToFile(dataUsed, usageStartTimeSec);
    return dataUsed;

  }

  /**
   * Helper function: given the beginning of the data usage period currrently 
   * under consideration, determine if we're still in that period.
   * 
   * @param usageStartTimeSec The start of the last stored data usage period
   * @return True if we are still in the same data usage period.
   */
  private boolean isInDataLimitPeriod(long usageStartTimeSec) {
    long timeSoFar = (System.currentTimeMillis() / 1000) - usageStartTimeSec;
    Logger.i("Time passed since data period last changed: " + timeSoFar);
    return timeSoFar <= Config.DEFAULT_DATA_MONITOR_PERIOD_DAY * 24 * 60 * 60;        
  }

  /**
   * Determines if the data limit has been exceeded.
   * 
   * If there is no data limit, always returns false.
   * If there is no valid data usage file,
   * creates a new one and returns false.
   * 
   * Otherwise, checks if we are over the limit yet or if we can run another task.
   * If a new data period needs to be started, we do that too.     * 
   * 
   * @param nextTaskType In the case of a TCP throughput task, we only run it if there is
   * enough data left.
   * @return True if over the data limit
   * @throws IOException
   */
  private boolean isOverDataLimit(String nextTaskType) throws IOException {
    Logger.i("Checking data limit...");

    if (getDataLimit() == UNLIMITED_LIMIT) {
      Logger.i("No data limit!");
      return false;
    } 
    long[] usagedata = readUsageFromFile();
    long usageStartTimeSec = usagedata[0];
    long dataUsed = usagedata[1];

    if (usageStartTimeSec != -1) {
      if (!isInDataLimitPeriod(usageStartTimeSec)) {
        // Update our file to the next period, and update our data usage 
        // budget accordingly.
        dataUsed = setNewDataConsumptionPeriod(dataUsed, usageStartTimeSec);
      }            
      long dataLimit = (getDataLimit() * Config.DEFAULT_DATA_MONITOR_PERIOD_DAY) / 30;
      Logger.i("Data limit is: " + dataLimit + " Data used is:" + dataUsed);
      if (dataUsed >= dataLimit) {
        Logger.i("Exceeded data limit:  Total data limit:"
            + getDataLimit());
        return true;
      } else {
        return false;
      }
    }
    // If the file wasn't there we need to reset the data limit period.
    resetDataUsage();
    return false;
  }

  /**
   * Determine how much data was consumed by a task and update the 
   * data usage accordingly.
   * 
   * @param result Structure holding the measurement result from which we can extract data usage.
   * @param taskType The type of measurement task completed
   * @throws IOException
   */
  public void updateDataUsage(long taskDataUsed)
      throws IOException {

    Logger.i("Amount of data used in the last task: " + taskDataUsed);

    long[] usagedata = readUsageFromFile();
    long usageStartTimeSec = usagedata[0];
    long dataUsed = usagedata[1];
    // If we have a valid file
    if (usageStartTimeSec != -1) {
      dataUsed += taskDataUsed;
      if (! isInDataLimitPeriod(usageStartTimeSec)) {
        // If we are in a new data consumption period, update it
        setNewDataConsumptionPeriod(dataUsed, usageStartTimeSec);
      } else {
        // Otherwise just write to a file
        writeDataUsageToFile(dataUsed, usageStartTimeSec);   
      }
    } else {
      // If we don't have a data usage file, initialize it with the data just used
      Logger.i("Data usage file not found, creating a new one...");
      usageStartTimeSec = (System.currentTimeMillis() / 1000);
      dataUsed = taskDataUsed;
      writeDataUsageToFile(dataUsed, usageStartTimeSec);
    }
  }



  /** 
   * Returns whether a measurement can be run.
   */
  public synchronized boolean canScheduleExperiment() {
    return (PhoneUtils.getPhoneUtils().isCharging() || 
        PhoneUtils.getPhoneUtils().getCurrentBatteryLevel() > minBatteryThreshold);
  }

  /**
   * A task wrapper that is power aware, the real logic is carried out by realTask
   * 
   * @author wenjiezeng@google.com (Steve Zeng)
   *
   */
  public static class PowerAwareTask implements Callable<MeasurementResult> {

    private MeasurementTask realTask;
    private ResourceCapManager pManager;
    private MeasurementScheduler scheduler;

    public PowerAwareTask(MeasurementTask task, ResourceCapManager manager, 
                          MeasurementScheduler scheduler) {
      realTask = task;
      pManager = manager;
      this.scheduler = scheduler;
    }

    private void broadcastMeasurementStart() {
      Logger.i("Starting PowerAwareTask " + realTask);
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.STATUS_MSG_PAYLOAD, "Running " + realTask.getDescriptor());

      scheduler.sendBroadcast(intent);
    }

    private void broadcastMeasurementEnd(MeasurementResult result, MeasurementError error) {
      Logger.i("Ending PowerAwareTask " + realTask);
      // Only broadcast information about measurements if they are true errors.
      if (!(error instanceof MeasurementSkippedException)) {
        Intent intent = new Intent();
        intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, 
          (int) realTask.getDescription().priority);
        // A progress value MEASUREMENT_END_PROGRESS indicates the end of an measurement
        intent.putExtra(UpdateIntent.PROGRESS_PAYLOAD, Config.MEASUREMENT_END_PROGRESS);


        if (result != null) {
          intent.putExtra(UpdateIntent.STRING_PAYLOAD, result.toString());					
        } else {
          String errorString = "Measurement " + realTask.toString() + " failed. ";
          errorString += "\n\nTimestamp: " + Calendar.getInstance().getTime();
          if (error != null) {
            errorString += "\n\n" + error.toString();
          } 
          intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);
        }

        // We now store results as strings to disk immediately to avoid data
        // losses on a crash, so convert to a JSON and sent back
        try {
          intent.putExtra(UpdateIntent.RESULT_PAYLOAD, 
            MeasurementJsonConvertor.encodeToJson(result).toString());
        } catch (JSONException e) {
          e.printStackTrace();
        }

        scheduler.sendBroadcast(intent);
      }
      scheduler.updateStatus();
    }

    @Override
    public MeasurementResult call() throws MeasurementError {
      MeasurementResult result = null;
      scheduler.sendStringMsg("Running:\n" + realTask.toString());
      try {
        PhoneUtils.getPhoneUtils().acquireWakeLock();
        if (scheduler.isPauseRequested()) {
          Logger.i("Skipping measurement - scheduler paused");
          throw new MeasurementSkippedException("Scheduler paused");
        }
        if (!pManager.canScheduleExperiment()) {
          Logger.i("Skipping measurement - low battery");
          throw new MeasurementSkippedException("Not enough battery power");
        }

        if (PhoneUtils.getPhoneUtils().getCurrentNetworkConnection()==PhoneUtils.TYPE_MOBILE){
          try {
            if(pManager.isOverDataLimit(realTask.getMeasurementType())) {
              scheduler.sendStringMsg("No cellular data is available for a server " +
                  realTask.getDescription().type+" task");
              Logger.i("Skipping measurement - data limit is passed");
              throw new MeasurementSkippedException("Over data limit");
            }
          } catch (IOException e) {
            Logger.e("Exception occured during R/Wing of data stat file");
            e.printStackTrace();
          }

        }
        scheduler.setCurrentTask(realTask);
        broadcastMeasurementStart();
        try {
          Logger.i("Calling PowerAwareTask " + realTask);
          pManager.updateDataUsage(PHONEUTILCOST);
          result = realTask.call(); 
          Logger.i("Got result " + result);
          // We only care about the data usage when on the mobile network
          if (PhoneUtils.getPhoneUtils().getCurrentNetworkConnection()==PhoneUtils.TYPE_MOBILE){
            pManager.updateDataUsage(realTask.getDataConsumed());
          }
          broadcastMeasurementEnd(result, null);
          return result;
        } catch (MeasurementError e) {
          Logger.e("Got MeasurementError running task", e);
          broadcastMeasurementEnd(null, e);
          throw e;
        } catch (Exception e) {
          Logger.e("Got exception running task", e);
          MeasurementError err = new MeasurementError("Got exception running task", e);
          broadcastMeasurementEnd(null, err);
          throw err;
        }
      } finally {
        PhoneUtils.getPhoneUtils().releaseWakeLock();
        scheduler.setCurrentTask(null);
        scheduler.sendStringMsg("Done running:\n" + realTask.toString());
      }
    }
  }
}

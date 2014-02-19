/*
 * Copyright 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.mobiperf;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.myjson.reflect.TypeToken;
import com.mobiperf.ResourceCapManager.DataUsageProfile;
import com.mobiperf.ResourceCapManager.PowerAwareTask;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;
import com.mobiperf.R;

/**
 * The single scheduler thread that monitors the task queue, runs tasks at their specified times,
 * and finally retrieves and reports results once they finish.
 * 
 * All method invocations on the singleton object are thread-safe.
 */
public class MeasurementScheduler extends Service {

  // This arbitrary id is private to Speedometer
  private static final int NOTIFICATION_ID = 1234;

  private ExecutorService measurementExecutor;
  private BroadcastReceiver broadcastReceiver;
  private Boolean pauseRequested = true;
  private boolean stopRequested = false;
  private boolean isSchedulerStarted = false;
  private Checkin checkin;
  private long checkinIntervalSec;
  private long checkinRetryIntervalSec;
  private int checkinRetryCnt;
  private CheckinTask checkinTask;
  private Calendar lastCheckinTime;

  private PendingIntent checkinIntentSender;
  /**
   * Intent for checkin retries. Reusing checkinIntentSender for retries will cancel any previously
   * configured periodic checkin schedule. Thus we need a separate intent sender
   */
  private PendingIntent checkinRetryIntentSender;
  private PendingIntent measurementIntentSender;
  private AlarmManager alarmManager;
  private ResourceCapManager resourceCapManager;
  /*
   * Both taskQueue and pendingTasks are thread safe and operations on them are atomic. To guarantee
   * reliable value propagation between threads, use volatile keyword.
   */
  private volatile PriorityBlockingQueue<MeasurementTask> taskQueue;
  private volatile ConcurrentHashMap<MeasurementTask, Future<MeasurementResult>> pendingTasks;
  // Binder given to clients
  private final IBinder binder = new SchedulerBinder();

  private MeasurementTask currentTask;

  private NotificationManager notificationManager;
  private int completedMeasurementCnt = 0;
  private int failedMeasurementCnt = 0;

  private ArrayList<String> userResults;
  private ArrayList<String> systemResults;
  private ArrayList<String> systemConsole;
  
  // We keep track of the current tasks running, indexed by their unique IDs,
  // for the purpose of selectively updating our schedule when new tasks are
  // received from the server
  private Hashtable<String, MeasurementTask> currentSchedule;

  private PhoneUtils phoneUtils;

  /**
   * The Binder class that returns an instance of running scheduler
   */
  public class SchedulerBinder extends Binder {
    public MeasurementScheduler getService() {
      return MeasurementScheduler.this;
    }
  }

  /*
   * Returns a IBinder that contains the instance of the MeasurementScheduler object
   * 
   * @see android.app.Service#onBind(android.content.Intent)
   */
  @Override
  public IBinder onBind(Intent intent) {
    Logger.d("Service onBind called");
    return this.binder;
  }

  // Service objects are by nature singletons enforced by Android
  @Override
  public void onCreate() {
    Logger.d("Service onCreate called");
    PhoneUtils.setGlobalContext(this.getApplicationContext());
    phoneUtils = PhoneUtils.getPhoneUtils();
    phoneUtils.registerSignalStrengthListener();
    this.checkin = new Checkin(this);
    this.checkinRetryIntervalSec = Config.MIN_CHECKIN_RETRY_INTERVAL_SEC;
    this.checkinRetryCnt = 0;
    this.checkinTask = new CheckinTask();

    this.pauseRequested = true;
    this.stopRequested = false;
    this.measurementExecutor = Executors.newSingleThreadExecutor();
    this.taskQueue =
        new PriorityBlockingQueue<MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE,
            new TaskComparator());
    this.pendingTasks =
        new ConcurrentHashMap<MeasurementTask, Future<MeasurementResult>>();
    
    // expect it to be the same size as the queue
    this.currentSchedule =
        new Hashtable<String, MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE);

    this.notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    this.alarmManager =
        (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
    this.resourceCapManager =
        new ResourceCapManager(Config.DEFAULT_BATTERY_THRESH_PRECENT, this);

    restoreState();

    // Register activity specific BroadcastReceiver here
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.PREFERENCE_ACTION);
    filter.addAction(UpdateIntent.MSG_ACTION);
    filter.addAction(UpdateIntent.CHECKIN_ACTION);
    filter.addAction(UpdateIntent.CHECKIN_RETRY_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);

    broadcastReceiver = new BroadcastReceiver() {
      // Handles various broadcast intents.

      // If traffic is paused by RRCTrafficControl (because a RRC test is
      // running), we do not perform the checkin, since sending interfering
      // traffic makes the RRC inference task abort and restart the current
      // test as the traffic may have altered the phone's RRC state.
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(UpdateIntent.PREFERENCE_ACTION)) {
          updateFromPreference();
        } else if (intent.getAction().equals(UpdateIntent.CHECKIN_ACTION)
            || intent.getAction().equals(UpdateIntent.CHECKIN_RETRY_ACTION)
            && !RRCTrafficControl.checkIfPaused()) {
          Logger.d("Checkin intent received");
          handleCheckin(false);
        } else if (intent.getAction().equals(UpdateIntent.MEASUREMENT_ACTION)
            && !RRCTrafficControl.checkIfPaused()) {
          Logger.d("MeasurementIntent intent received");
          handleMeasurement();
        } else if (intent.getAction().equals(
            UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION)) {
          Logger.d("MeasurementIntent update intent received");
          if (intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD,
              Config.INVALID_PROGRESS) == Config.MEASUREMENT_END_PROGRESS) {
            if (intent.getStringExtra(UpdateIntent.ERROR_STRING_PAYLOAD) != null) {
              failedMeasurementCnt++;
            } else {
              // Process result
              completedMeasurementCnt++;
            }
            if (intent.getStringExtra(UpdateIntent.RESULT_PAYLOAD) != null) {
              Logger.d("Measurement result intent received");
              saveResultToFile(intent
                  .getStringExtra(UpdateIntent.RESULT_PAYLOAD));

            }
            updateResultsConsole(intent);
          }
        } else if (intent.getAction().equals(UpdateIntent.MSG_ACTION)) {
          String msg =
              intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD);
          Date now = Calendar.getInstance().getTime();
          insertStringToConsole(systemConsole, now + "\n\n" + msg);
        }
      }
    };
    this.registerReceiver(broadcastReceiver, filter);
    // TODO(mdw): Make this a user-selectable option
    addIconToStatusBar();
  }

  public boolean hasBatteryToScheduleExperiment() {
    return resourceCapManager.canScheduleExperiment();
  }

  /**
   * Create notification that indicates the service is running.
   */
  private Notification createServiceRunningNotification() {
    // The intent to launch when the user clicks the expanded notification
    Intent intent = new Intent(this, SpeedometerApp.class);
    PendingIntent pendIntent =
        PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT);

    // This constructor is deprecated in 3.x. But most phones still run 2.x systems
    Notification notice =
        new Notification(R.drawable.icon_statusbar,
            getString(R.string.notificationSchedulerStarted),
            System.currentTimeMillis());
    notice.flags |=
        Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

    // This is deprecated in 3.x. But most phones still run 2.x systems
    notice.setLatestEventInfo(this, getString(R.string.app_name),
        getString(R.string.notificationServiceRunning), pendIntent);
    return notice;
  }

  /**
   * Add an icon to the device status bar.
   */
  private void addIconToStatusBar() {
    notificationManager.notify(NOTIFICATION_ID,
        createServiceRunningNotification());
  }

  /**
   * Remove the icon from the device status bar.
   */
  private void removeIconFromStatusBar() {
    notificationManager.cancel(NOTIFICATION_ID);
  }

  /**
   * Keep the service in the foreground, preventing it from being killed in low-memory situations.
   */
  @SuppressWarnings("unused")
  private void startSpeedometerInForeGround() {
    Logger.d("Service startSpeedometerInForeGround called");

    // Put scheduler service into foreground. Makes the process less likely of being killed
    startForeground(NOTIFICATION_ID, createServiceRunningNotification());
  }

  /**
   * Perform a checkin operation.
   */
  public void handleCheckin(boolean force) {
    if (!userConsented()) {
      Logger.i("Skipping checkin - User has not consented");
      return;
    }

    // New addition: check if the RRC task has paused other tasks.
    if ((!force && isPauseRequested()) || RRCTrafficControl.checkIfPaused()) {
      sendStringMsg("Skipping checkin - app is paused");
      return;
    }
    if (!force && !resourceCapManager.canScheduleExperiment()) {
      sendStringMsg("Skipping checkin - below battery threshold");
      return;
    }
    /*
     * The CPU can go back to sleep immediately after onReceive() returns. Acquire the wake lock for
     * the new thread here and release the lock when the thread finishes
     */
    PhoneUtils.getPhoneUtils().acquireWakeLock();
    new Thread(checkinTask).start();
  }

  private void handleMeasurement() {
    if (!userConsented()) {
      Logger.i("Skipping measurement - User has not consented");
      return;
    }

    try {
      MeasurementTask task = taskQueue.peek();
      // Process the head of the queue.
      if (task != null && task.timeFromExecution() <= 0) {
        taskQueue.poll();
        Future<MeasurementResult> future;
        Logger.i("Processing task " + task.toString());
        // Run the head task using the executor
        if (task.getDescription().priority == MeasurementTask.USER_PRIORITY) {
          sendStringMsg("Scheduling user task:\n" + task);
          // User task can override the power policy. So a different task wrapper is used.
          future = measurementExecutor.submit(new UserMeasurementTask(task));
        } else {
          sendStringMsg("Scheduling task:\n" + task);
          future =
              measurementExecutor.submit(new PowerAwareTask(task, resourceCapManager,
                  this));
        }
        synchronized (pendingTasks) {
          pendingTasks.put(task, future);
        }

        MeasurementDesc desc = task.getDescription();

        long newStartTime =
            desc.startTime.getTime() + (long) desc.intervalSec * 1000;

        // Add a clone of the task if it's still valid.
        if (newStartTime < desc.endTime.getTime()
            && (desc.count == MeasurementTask.INFINITE_COUNT || desc.count > 1)) {
          MeasurementTask newTask = task.clone();
          if (desc.count != MeasurementTask.INFINITE_COUNT) {
            newTask.getDescription().count--;
          }
          newTask.getDescription().startTime.setTime(newStartTime);
          submitTask(newTask);
        }
      }
      // Schedule the next measurement in the taskQueue
      task = taskQueue.peek();
      if (task != null) {
        long timeFromExecution =
            Math.max(task.timeFromExecution(),
                Config.MIN_TIME_BETWEEN_MEASUREMENT_ALARM_MSEC);
        measurementIntentSender =
            PendingIntent.getBroadcast(this, 0, new UpdateIntent("",
                UpdateIntent.MEASUREMENT_ACTION),
                PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
            + timeFromExecution, measurementIntentSender);
      }
    } catch (IllegalArgumentException e) {
      // Task creation in clone can create this exception
      Logger.e("Exception when cloning task");
      sendStringMsg("Exception when cloning task: " + e);
    } catch (Exception e) {
      // We don't want any unexpected exception to crash the process
      Logger.e("Exception when handling measurements", e);
      sendStringMsg("Exception running task: " + e);
    }
    persistState();
  }

  /**
   * Sets the current task being run. In the current implementation, the synchronized keyword is not
   * needed because only one thread runs measurements and calls this method. It is not thread safe.
   */
  public void setCurrentTask(MeasurementTask task) {
    this.currentTask = task;
  }

  /**
   * Returns the current task being run. In the current implementation, the synchronized keyword is
   * not needed because only one thread runs measurements and calls this method. It is not thread
   * safe.
   */
  public MeasurementTask getCurrentTask() {
    return this.currentTask;
  }

  /**
   * Removes the first task in the taskQueue with the taskKey
   */
  public boolean removeTaskByKey(String taskKey) {
    Iterator<MeasurementTask> it = taskQueue.iterator();
    while (it.hasNext()) {
      MeasurementTask task = it.next();
      if (task.getDescription().key.equals(taskKey)) {
        it.remove();
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the current task queue in the scheduler.
   */
  public PriorityBlockingQueue<MeasurementTask> getTaskQueue() {
    return taskQueue;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Logger.d("Service onStartCommand called, isSchedulerStarted = "
        + isSchedulerStarted);
    // Start up the thread running the service. Using one single thread for all requests
    Logger.i("starting scheduler");
    sendStringMsg("Scheduler starting");
    if (!isSchedulerStarted) {
      restoreState();
      updateFromPreference();
      this.resume();
      /*
       * There is no onStop() for services. The service is only stopped when the user exits the
       * application. So don't worry about setting isSchedulerStarted to false.
       */
      isSchedulerStarted = true;
    }
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    Logger.d("Service onDestroy called");
    super.onDestroy();
    cleanUp();
  }

  /**
   * Returns the power manager used by the scheduler
   * */
  public ResourceCapManager resourceCapManager() {
    return this.resourceCapManager;
  }

  /** Set the interval for checkin in seconds */
  public synchronized void setCheckinInterval(long interval) {
    this.checkinIntervalSec =
        Math.max(Config.MIN_CHECKIN_INTERVAL_SEC, interval);
    // the new checkin schedule will start in PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC seconds
    checkinIntentSender =
        PendingIntent.getBroadcast(this, 0, new UpdateIntent("",
            UpdateIntent.CHECKIN_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
        System.currentTimeMillis() + Config.PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC,
        checkinIntervalSec * 1000, checkinIntentSender);

    Logger.i("Setting checkin interval to " + interval + " seconds");
  }

  /** Returns the checkin interval of the scheduler in seconds */
  public synchronized long getCheckinInterval() {
    return this.checkinIntervalSec;
  }

  /** Returns the last checkin time */
  public synchronized Date getLastCheckinTime() {
    if (lastCheckinTime != null) {
      return lastCheckinTime.getTime();
    } else {
      return null;
    }
  }

  /** Returns the next (expected) checkin time */
  public synchronized Date getNextCheckinTime() {
    if (lastCheckinTime != null) {
      Calendar nextCheckinTime = (Calendar) lastCheckinTime.clone();
      nextCheckinTime.add(Calendar.SECOND, (int) getCheckinInterval());
      return nextCheckinTime.getTime();
    } else {
      return null;
    }
  }

  /**
   * Prevents new tasks from being scheduled. Started task will still run to finish.
   */
  public synchronized void pause() {
    Logger.d("Service pause called");
    sendStringMsg("Scheduler pausing");
    this.pauseRequested = true;
    updateStatus();
  }

  /** Enables new tasks to be scheduled */
  public synchronized void resume() {
    Logger.d("Service resume called");
    sendStringMsg("Scheduler resuming");
    this.pauseRequested = false;
    updateStatus();
  }

  /** Return whether new tasks can be scheduled */
  public synchronized boolean isPauseRequested() {
    return this.pauseRequested;
  }

  /** Remove all tasks that have not been scheduled */
  public synchronized void removeAllUnscheduledTasks() {
    this.taskQueue.clear();
  }

  /** Return the number of tasks that have not been scheduled */
  public int getUnscheduledTaskCount() {
    return this.taskQueue.size();
  }

  /** Return the next task to be scheduled */
  public MeasurementTask getNextTaskToBeScheduled() {
    return this.taskQueue.peek();
  }

  /** Return the number of pending tasks that have been scheduled */
  public int getPendingTaskCount() {
    return this.pendingTasks.size();
  }

  private class TaskComparator implements Comparator<MeasurementTask> {

    @Override
    public int compare(MeasurementTask task1, MeasurementTask task2) {
      return task1.compareTo(task2);
    }
  }

  /** Request the scheduler to stop execution. */
  public synchronized void requestStop() {
    sendStringMsg("Scheduler stop requested");
    this.stopRequested = true;
    this.notifyAll();
    this.stopForeground(true);
    this.removeIconFromStatusBar();
    this.stopSelf();
  }

  /**
   * Submit a MeasurementTask to the scheduler. Caller of this method can broadcast an intent with
   * MEASUREMENT_ACTION to start the measurement immediately.
   */
  public boolean submitTask(MeasurementTask task) {
    try {
      // Immediately handles measurements created by user
      if (task.getDescription().priority == MeasurementTask.USER_PRIORITY) {
        return this.taskQueue.add(task);
      }

      if (taskQueue.size() >= Config.MAX_TASK_QUEUE_SIZE
          || pendingTasks.size() >= Config.MAX_TASK_QUEUE_SIZE) {
        return false;
      }
      // Automatically notifies the scheduler waiting on taskQueue.take()
      return this.taskQueue.add(task);
    } catch (NullPointerException e) {
      Logger.e("The task to be added is null");
      return false;
    } catch (ClassCastException e) {
      Logger.e("cannot compare this task against existing ones");
      return false;
    }
  }

  @SuppressWarnings("unused")
  private void updateNotificationBar(String notificationMsg) {
    // The intent to launch when the user clicks the expanded notification
    Intent intent = new Intent(this, SpeedometerApp.class);
    PendingIntent pendIntent =
        PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_CANCEL_CURRENT);

    // This constructor is deprecated in 3.x. But most phones still run 2.x systems
    Notification notice =
        new Notification(R.drawable.icon_statusbar, notificationMsg,
            System.currentTimeMillis());

    // This is deprecated in 3.x. But most phones still run 2.x systems
    notice.setLatestEventInfo(this, "Speedometer", notificationMsg, pendIntent);

    notificationManager.notify(NOTIFICATION_ID, notice);
  }

  /**
   * Broadcast an intent to update the system status.
   */
  public void updateStatus() {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
    String statsMsg =
        completedMeasurementCnt + " completed, " + failedMeasurementCnt
            + " failed";
    intent.putExtra(UpdateIntent.STATS_MSG_PAYLOAD, statsMsg);
    sendBroadcast(intent);
  }

  private void updateFromPreference() {
    Logger.d("Service updateFromPreference called");
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    try {
      resourceCapManager.setBatteryThresh(Integer.parseInt(prefs.getString(
          getString(R.string.batteryMinThresPrefKey),
          String.valueOf(Config.DEFAULT_BATTERY_THRESH_PRECENT))));

      // Fetch the data limit with 250 MB as a default
      resourceCapManager.setDataUsageLimit(prefs.getString(
          getString(R.string.dataLimitPrefKey), "250 MB"));


      this.setCheckinInterval(Integer.parseInt(prefs.getString(
          getString(R.string.checkinIntervalPrefKey),
          String.valueOf(Config.DEFAULT_CHECKIN_INTERVAL_SEC / 3600))) * 3600);

      updateStatus();

      Logger.i("Preference set from SharedPreference: " + "checkinInterval="
          + checkinIntervalSec + ", minBatThres= "
          + resourceCapManager.getBatteryThresh());
    } catch (ClassCastException e) {
      Logger.e("exception when casting preference values", e);
    }
  }

  /**
   * Write a string to the system console.
   */
  public void sendStringMsg(String str) {
    UpdateIntent intent = new UpdateIntent(str, UpdateIntent.MSG_ACTION);
    this.sendBroadcast(intent);
  }

  private synchronized void cleanUp() {
    Logger.d("Service cleanUp called");
    this.taskQueue.clear();

    if (this.currentTask != null) {
      this.currentTask.stop();
    }
    // remove all future tasks
    this.measurementExecutor.shutdown();
    // remove and stop all active tasks
    this.measurementExecutor.shutdownNow();
    this.checkin.shutDown();

    this.unregisterReceiver(broadcastReceiver);
    Logger.d("canceling pending intents");

    if (checkinIntentSender != null) {
      checkinIntentSender.cancel();
      alarmManager.cancel(checkinIntentSender);
    }
    if (checkinRetryIntentSender != null) {
      checkinRetryIntentSender.cancel();
      alarmManager.cancel(checkinRetryIntentSender);
    }
    if (measurementIntentSender != null) {
      measurementIntentSender.cancel();
      alarmManager.cancel(measurementIntentSender);
    }
    persistState();
    this.notifyAll();
    phoneUtils.shutDown();

    removeIconFromStatusBar();

    Logger.i("Shut down all executors and stopping service");
  }

  private void resetCheckin() {
    // reset counters for checkin
    checkinRetryCnt = 0;
    checkinRetryIntervalSec = Config.MIN_CHECKIN_RETRY_INTERVAL_SEC;
    checkin.initializeAccountSelector();
  }

  private void getTasksFromServer() throws IOException {
    Logger.i("Downloading tasks from the server");
    // Do not download new tasks while the RRC task is running
    // to avoid interference
    if (RRCTrafficControl.checkIfPaused()) {
      return;
    }
    checkin.getCookie();
    List<MeasurementTask> tasksFromServer = checkin.checkin(resourceCapManager);

    updateSchedule(tasksFromServer, false);

  }

  /**
   * Adjusts the frequency of the task based on the profile passed from the server.
   * 
   * Alternately, disregards the task altogether, if a -1 is passed.
   * 
   * @param task The task to adjust
   * @return false if the task should not be scheduled, based on the profile
   */
  private boolean adjustInterval(MeasurementTask task) {

    Map<String, String> params = task.getDescription().parameters;
    float adjust = 1; // default
    if (params.containsKey("profile_1_freq")
        && resourceCapManager.getDataUsageProfile() == DataUsageProfile.PROFILE1) {
      adjust = Float.parseFloat(params.get("profile_1_freq"));
      Logger.i("Task " + task.getDescription().key
          + " adjusted using profile 1");
    } else if (params.containsKey("profile_2_freq")
        && resourceCapManager.getDataUsageProfile() == DataUsageProfile.PROFILE2) {
      adjust = Float.parseFloat(params.get("profile_2_freq"));
      Logger.i("Task " + task.getDescription().key
          + " adjusted using profile 2");
    } else if (params.containsKey("profile_3_freq")
        && resourceCapManager.getDataUsageProfile() == DataUsageProfile.PROFILE3) {
      adjust = Float.parseFloat(params.get("profile_3_freq"));
      Logger.i("Task " + task.getDescription().key
          + " adjusted using profile 3");
    } else if (params.containsKey("profile_4_freq")
        && resourceCapManager.getDataUsageProfile() == DataUsageProfile.PROFILE4) {
      adjust = Float.parseFloat(params.get("profile_4_freq"));
      Logger.i("Task " + task.getDescription().key
          + " adjusted using profile 4");
    } else if (params.containsKey("profile_unlimited")
        && resourceCapManager.getDataUsageProfile() == DataUsageProfile.UNLIMITED) {
      adjust = Float.parseFloat(params.get("profile_unlimited"));
      Logger.i("Task " + task.getDescription().key
          + " adjusted using unlimited profile");
    }
    if (adjust <= 0) {
      Logger.i("Task " + task.getDescription().key + "marked for removal");
      return false;
    }
    task.getDescription().intervalSec *= adjust;
    task.getDescription().updateStartTime(); // Needed because the start time is set on creation
    return true;

  }

  /**
   * Update the schedule based on a set of tasks from the server.
   * <p>
   * The current tasks to schedule are in a hash table indexed by a unique task key.
   * <p>
   * Remove all tasks from the schedule that are not in the new list or that have changed.
   * Then, add all tasks from the new list that were not in the schedule, or have changed.
   * Then, the schedule will match the one in the server, and unchanged tasks are left as they are.
   * 
   * <p>
   * If the state has changed and the schedule was received from the server, save it to disk
   * so it can be recovered in case of a crash. 
   * 
   * @param newTasks List of MeasurementTasks from the server
   * @param reLoad if it's True, we're loading from disk: don't adjust frequencies or save to disk again.
   */
  private void updateSchedule(List<MeasurementTask> newTasks, boolean reLoad) {

    // Keep track of what tasks need to be added.
    // Altered tasks are removed and then added, so they go here too
    Vector<MeasurementTask> tasksToAdd = new Vector<MeasurementTask>();

    // Keep track of what keys are not being used. Remove keys from this as
    // you find they are in use.
    Set<String> missingKeys = new HashSet<String>(currentSchedule.keySet());
    Set<String> keysToRemove = new HashSet<String>();

    Logger.i("Attempting to add new tasks");

    for (MeasurementTask newTask : newTasks) {

      // Adjust the frequency of the new task, based on the selected data consumption profile,
      // or ignore it if the task is disabled for this profile.
      // If we are loading again, don't re-adjust task frequencies.
      if (!reLoad) {
        if (!adjustInterval(newTask)) {
          continue;
        }
      }

      String newKey = newTask.getDescription().key;
      if (!missingKeys.contains(newKey)) {
        tasksToAdd.add(newTask);
      } else {
        // check for changes. If any parameter changes, it counts as a change.
        if (!currentSchedule.get(newKey).getDescription()
            .equals(newTask.getDescription())) {
          // If there's a change, replace the task with the new task from the server
          keysToRemove.add(newKey);
          tasksToAdd.add(newTask);
        }
        // We've seen the task
        missingKeys.remove(newKey);
      }
    }

    // scheduleKeys now contain all keys that do not exist
    keysToRemove.addAll(missingKeys);

    // Add all new tasks, and copy all unmodified tasks, to a new queue.
    // Also update currentSchedule accordingly.
    PriorityBlockingQueue<MeasurementTask> newQueue =
        new PriorityBlockingQueue<MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE,
            new TaskComparator());

    synchronized (currentSchedule) {
      Logger.i("Tasks to remove:" + keysToRemove.size());
      for (MeasurementTask task : this.taskQueue) {
        String taskKey = task.getDescription().key;
        if (!keysToRemove.contains(taskKey)) {
          newQueue.add(task);
        } else {
          Logger.w("Removing task with key" + taskKey);
          // Also need to keep our master schedule up to date
          currentSchedule.remove(taskKey);
        }
      }
      this.taskQueue = newQueue;
      // add all new tasks
      Logger.i("New tasks added:" + tasksToAdd.size());
      for (MeasurementTask task : tasksToAdd) {
        submitTask(task);
        currentSchedule.put(task.getDescription().key, task);
      }
    }

    if (!reLoad && (!tasksToAdd.isEmpty() || !keysToRemove.isEmpty())) {
      saveSchedulerState();
    }
  }

  /**
   * Save the results of a task to a file, for later uploading.
   * This way, if the application crashes, is halted, etc. between the
   * task and checkin, no results are lost.
   * 
   * @param result The JSON representation of a result, as a string
   */
  private synchronized void saveResultToFile(String result) {
    try {
      Logger.i("Saving result to file...");
      BufferedOutputStream writer =
          new BufferedOutputStream(openFileOutput("results",
              Context.MODE_PRIVATE | Context.MODE_APPEND));
      result += "\n";
      writer.write(result.getBytes());
      writer.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Read in the results of tasks completed to date from a file, then clear the file.
   * 
   * @return The results as a JSONArray, ready for sending to the server.
   */
  private synchronized JSONArray readResultsFromFile() {

    JSONArray results = new JSONArray();
    try {
      Logger.i("Loading results from disk");
      FileInputStream inputstream = openFileInput("results");
      InputStreamReader streamreader = new InputStreamReader(inputstream);
      BufferedReader bufferedreader = new BufferedReader(streamreader);

      String line;
      while ((line = bufferedreader.readLine()) != null) {
        JSONObject jsonTask;
        try {
          jsonTask = new JSONObject(line);
          results.put(jsonTask);
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }

      bufferedreader.close();
      streamreader.close();
      inputstream.close();

      // delete file once done, to avoid uploading results twice
      deleteFile("results");


    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return results;
  }

  /**
   * Save the entire current schedule to a file, in JSON format, like how
   * tasks are received from the server.
   * 
   * One item per line.
   */
  private void saveSchedulerState() {
    synchronized (currentSchedule) {
      try {
        BufferedOutputStream writer =
            new BufferedOutputStream(openFileOutput("schedule",
                Context.MODE_PRIVATE));

        Logger.i("Saving schedule to a file...");
        for (Map.Entry<String, MeasurementTask> entry : currentSchedule
            .entrySet()) {
          try {
            JSONObject task =
                MeasurementJsonConvertor.encodeToJson(entry.getValue()
                    .getDescription());
            String taskstring = task.toString() + "\n";
            writer.write(taskstring.getBytes());
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
        writer.close();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Load the schedule from the schedule file, if it exists.
   * 
   * This is to be run when the app first starts up, so scheduled items
   * are not lost.
   */
  private void loadSchedulerState() {
    Vector<MeasurementTask> tasksToAdd = new Vector<MeasurementTask>();
    synchronized (currentSchedule) {
      try {
        Logger.i("Restoring schedule from disk...");
        FileInputStream inputstream = openFileInput("schedule");
        InputStreamReader streamreader = new InputStreamReader(inputstream);
        BufferedReader bufferedreader = new BufferedReader(streamreader);

        String line;
        while ((line = bufferedreader.readLine()) != null) {
          JSONObject jsonTask;
          try {
            jsonTask = new JSONObject(line);
            MeasurementTask newTask =
                MeasurementJsonConvertor.makeMeasurementTaskFromJson(jsonTask,
                    getApplicationContext());
            
            // If the task is scheduled in the past, re-schedule it in the future
            // We assume tasks in the past have run, otherwise we can wind up getting
            // stuck trying to run a large backlog of tasks
            
            long curtime = System.currentTimeMillis();
            if (curtime > newTask.getDescription().startTime.getTime()) {
                long timediff = curtime - newTask.getDescription().startTime.getTime();               
                
                timediff = (long) (timediff % (newTask.getDescription().intervalSec * 1000));
                Calendar now = Calendar.getInstance();
                now.add(Calendar.SECOND, (int) timediff/1000);
                newTask.getDescription().startTime.setTime(now.getTimeInMillis());
                Logger.i("Rescheduled task " + newTask.getDescription().key + 
                    " at time " + now.getTimeInMillis());
            }
            
            tasksToAdd.add(newTask);
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
        bufferedreader.close();
        streamreader.close();
        inputstream.close();

      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    updateSchedule(tasksToAdd, true);
  }


  @SuppressWarnings("unchecked")
  private void uploadResults() {
    MeasurementResult result;
    Future<MeasurementResult> future;
    JSONArray results = readResultsFromFile();

    synchronized (this.pendingTasks) {
      try {
        for (MeasurementTask task : this.pendingTasks.keySet()) {
          future = this.pendingTasks.get(task);
          if (future != null) {
            sendStringMsg("Finished:\n" + task);
            if (future.isDone()) {
              try {
                this.pendingTasks.remove(task);

                if (!future.isCancelled()) {
                  result = future.get();
                } else {
                  Logger.e("Task execution was canceled");
                  JSONObject cancelledResult =
                      MeasurementJsonConvertor.encodeToJson(this
                          .getFailureResult(task, new CancellationException(
                              "Task cancelled")));
                  results.put(cancelledResult);
                }

              } catch (InterruptedException e) {
                Logger.e("Task execution interrupted", e);
              } catch (ExecutionException e) {
                if (e.getCause() instanceof MeasurementSkippedException) {
                  // Don't do anything with this - no need to report skipped measurements
                  sendStringMsg("Task skipped - " + e.getCause().toString()
                      + "\n" + task);
                  Logger.i("Task skipped", e.getCause());
                } else {
                  // Log the error
                  sendStringMsg("Task failed - " + e.getCause().toString()
                      + "\n" + task);
                  Logger.e("Task execution failed", e.getCause());
                  // Was already sent
                  // finishedTasks.add(this.getFailureResult(task, e.getCause()));
                }
              } catch (CancellationException e) {
                Logger.e("Task cancelled", e);
              }
            } else if (task.isPassedDeadline()) {
              /*
               * If a task has reached its deadline but has not been run, remove it and report
               * failure
               */
              this.pendingTasks.remove(task);
              future.cancel(true);
              JSONObject cancelledResult =
                  MeasurementJsonConvertor.encodeToJson(this.getFailureResult(
                      task, new RuntimeException(
                          "Deadline passed before execution")));
              results.put(cancelledResult);
            }
          }

          if (future == null) {
            /*
             * Tasks that are scheduled after deadline are put into pendingTasks with a null future.
             */
            this.pendingTasks.remove(task);
            JSONObject cancelledResult =
                MeasurementJsonConvertor.encodeToJson(this
                    .getFailureResult(task, new RuntimeException(
                        "Task scheduled after deadline")));
            results.put(cancelledResult);
          }
        }
      } catch (ConcurrentModificationException e) {
        /*
         * keySet is a synchronized view of the keys. However, changes during iteration will throw
         * ConcurrentModificationException. Since we have synchronized all changes to pendingTasks
         * this should not happen.
         */
        Logger.e("Pending tasks is changed during measurement upload");
      } catch (JSONException e) {
        e.printStackTrace();
      }
    }

    if (results.length() > 0) {
      try {
        this.checkin.uploadMeasurementResult(results, resourceCapManager);
      } catch (IOException e) {
        Logger.e("Error when uploading message");
      }
    }

    Logger.i("A total of " + results.length() + " uploaded");
    Logger.i("A total of " + results.length() + " is in the results list");
  }

  private class CheckinTask implements Runnable {
    @Override
    public void run() {
      Logger.i("checking Speedometer service for new tasks");
      lastCheckinTime = Calendar.getInstance();
      try {
        persistState();
        uploadResults();
        getTasksFromServer();
        // Also reset checkin if we get a success
        resetCheckin();
        // Schedule the new tasks
        handleMeasurement();
      } catch (Exception e) {
        /*
         * Executor stops all subsequent execution of a periodic task if a raised exception is
         * uncaught. We catch all undeclared exceptions here
         */
        Logger.e("Unexpected exceptions caught", e);
        if (checkinRetryCnt > Config.MAX_CHECKIN_RETRY_COUNT) {
          /*
           * If we have retried more than MAX_CHECKIN_RETRY_COUNT times upon a checkin failure, we
           * will stop retrying and wait until the next checkin period
           */
          resetCheckin();
        } else if (checkinRetryIntervalSec < checkinIntervalSec) {
          Logger.i("Retrying checkin in " + checkinRetryIntervalSec
              + " seconds");
          /*
           * Use checkinRetryIntentSender so that the periodic checkin schedule will remain intact
           */
          checkinRetryIntentSender =
              PendingIntent.getBroadcast(MeasurementScheduler.this, 0,
                  new UpdateIntent("", UpdateIntent.CHECKIN_RETRY_ACTION),
                  PendingIntent.FLAG_CANCEL_CURRENT);
          alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
              + checkinRetryIntervalSec * 1000, checkinRetryIntentSender);
          checkinRetryCnt++;
          checkinRetryIntervalSec =
              Math.min(Config.MAX_CHECKIN_RETRY_INTERVAL_SEC,
                  checkinRetryIntervalSec * 2);
        }
      } finally {
        PhoneUtils.getPhoneUtils().releaseWakeLock();
        updateStatus();
      }
    }
  }

  @SuppressWarnings("unused")
  private synchronized boolean isStopRequested() {
    return this.stopRequested;
  }

  private String getStackTrace(Throwable error) {
    final Writer result = new StringWriter();
    final PrintWriter printWriter = new PrintWriter(result);
    error.printStackTrace(printWriter);
    return result.toString();
  }

  private MeasurementResult getFailureResult(MeasurementTask task,
      Throwable error) {
      
    try {
        resourceCapManager.updateDataUsage(ResourceCapManager.PHONEUTILCOST);
    } catch (IOException e) {
        e.printStackTrace();
    }
    
    MeasurementResult result =
        new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
            phoneUtils.getDeviceProperty(), task.getType(),
            System.currentTimeMillis() * 1000, false, task.measurementDesc);
    result.addResult("error", error.toString() + "\n" + getStackTrace(error));
    return result;
  }

  /**
   * A wrapper Callable class that broadcasts intents when the measurement starts and finishes.
   * Needed for activities to monitor the progress of user measurements.
   */
  private class UserMeasurementTask implements Callable<MeasurementResult> {
    MeasurementTask realTask;

    public UserMeasurementTask(MeasurementTask task) {
      realTask = task;
    }

    private void broadcastMeasurementStart() {
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD,
          MeasurementTask.USER_PRIORITY);
      MeasurementScheduler.this.sendBroadcast(intent);

      intent.setAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.STATUS_MSG_PAYLOAD, realTask.getDescriptor()
          + " is running. ");

      MeasurementScheduler.this.sendBroadcast(intent);
    }

    private void broadcastMeasurementEnd(MeasurementResult result) {
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD,
          MeasurementTask.USER_PRIORITY);
      // A progress value greater than max progress to indicate the termination of a measurement
      intent.putExtra(UpdateIntent.PROGRESS_PAYLOAD,
          Config.MEASUREMENT_END_PROGRESS);

      if (result != null) {
        intent.putExtra(UpdateIntent.STRING_PAYLOAD, result.toString());
      } else {
        String errorString =
            "Measurement " + realTask.getDescriptor() + " has failed";
        errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
        intent.putExtra(UpdateIntent.ERROR_STRING_PAYLOAD, errorString);
      }
      MeasurementScheduler.this.sendBroadcast(intent);
      // Update the status bar once the user measurement finishes
      updateStatus();
    }

    /**
     * The call() method that broadcast intents before the measurement starts and after the
     * measurement finishes.
     */
    @Override
    public MeasurementResult call() throws MeasurementError {
      MeasurementResult result = null;
      sendStringMsg("Running:\n" + realTask.toString());
      try {
        PhoneUtils.getPhoneUtils().acquireWakeLock();
        setCurrentTask(realTask);
        broadcastMeasurementStart();
        result = realTask.call();
      } finally {
        setCurrentTask(null);
        broadcastMeasurementEnd(result);
        PhoneUtils.getPhoneUtils().releaseWakeLock();
        sendStringMsg("Done running:\n" + realTask.toString());
        persistState();
      }
      return result;
    }
  }

  /**
   * Persist service state to prefs.
   */
  private synchronized void persistState() {
    Logger.d("Service persistState called");
    saveConsoleContent(systemResults, Config.PREF_KEY_SYSTEM_RESULTS);
    saveConsoleContent(userResults, Config.PREF_KEY_USER_RESULTS);
    saveConsoleContent(systemConsole, Config.PREF_KEY_SYSTEM_CONSOLE);
    saveStats();
  }

  /**
   * Restore service state from prefs.
   */
  private void restoreState() {
    Logger.d("Service restoreState called");
    initializeConsoles();
    restoreStats();
    loadSchedulerState();
  }


  /**
   * Save measurement statistics to persistent storage.
   */
  private void saveStats() {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    SharedPreferences.Editor editor = prefs.edit();
    editor.putInt(Config.PREF_KEY_COMPLETED_MEASUREMENTS,
        completedMeasurementCnt);
    editor.putInt(Config.PREF_KEY_FAILED_MEASUREMENTS, failedMeasurementCnt);
    editor.commit();
  }

  /**
   * Restore measurement statistics from persistent storage.
   */
  private void restoreStats() {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    completedMeasurementCnt =
        prefs.getInt(Config.PREF_KEY_COMPLETED_MEASUREMENTS, 0);
    failedMeasurementCnt = prefs.getInt(Config.PREF_KEY_FAILED_MEASUREMENTS, 0);
  }

  private boolean userConsented() {
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    boolean consented = prefs.getBoolean(Config.PREF_KEY_CONSENTED, false);
    Logger.i("userConsented returning " + consented);
    return consented;
  }

  /**
   * Persists the content of the console as a JSON string
   */
  private void saveConsoleContent(List<String> consoleContent, String prefKey) {
    Logger.d("Service saveConsoleContent for key " + prefKey);
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    SharedPreferences.Editor editor = prefs.edit();

    int length = consoleContent.size();
    Logger.d("Saving " + length + " entries to prefKey " + prefKey);
    ArrayList<String> items = new ArrayList<String>();
    // Since we use insertToConsole later on to restore the content, we have to store them
    // in the reverse order to maintain the same look
    for (int i = length - 1; i >= 0; i--) {
      items.add(consoleContent.get(i));
    }
    Type listType = new TypeToken<ArrayList<String>>() {}.getType();
    editor.putString(prefKey, MeasurementJsonConvertor.getGsonInstance()
        .toJson(items, listType));
    editor.commit();
  }

  /**
   * Restores the console content from the saved JSON string
   */
  private void initializeConsoles() {
    Logger.d("Service initializeConsoles called");

    systemResults = new ArrayList<String>();
    restoreConsole(systemResults, Config.PREF_KEY_SYSTEM_RESULTS);
    if (systemResults.size() == 0) {
      insertStringToConsole(systemResults,
          "Automatically-scheduled measurement results will " + "appear here.");
    }

    userResults = new ArrayList<String>();
    restoreConsole(userResults, Config.PREF_KEY_USER_RESULTS);
    if (userResults.size() == 0) {
      insertStringToConsole(userResults,
          "Your measurement results will appear here.");
    }

    systemConsole = new ArrayList<String>();
    restoreConsole(systemConsole, Config.PREF_KEY_SYSTEM_CONSOLE);
  }

  /**
   * Restores content for consoleContent with the key prefKey.
   */
  private void restoreConsole(List<String> consoleContent, String prefKey) {
    Logger.d("Service restoreConsole for " + prefKey);
    SharedPreferences prefs =
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    String savedConsole = prefs.getString(prefKey, null);
    if (savedConsole != null) {
      Type listType = new TypeToken<ArrayList<String>>() {}.getType();
      ArrayList<String> items =
          MeasurementJsonConvertor.getGsonInstance().fromJson(savedConsole,
              listType);
      if (items != null) {
        Logger.d("Read " + items.size() + " items from prefkey " + prefKey);
        for (String item : items) {
          insertStringToConsole(consoleContent, item);
        }
        Logger.d("Restored " + consoleContent.size() + " entries to console "
            + prefKey);
      }
    }
  }

  /**
   * Inserts a string into the console with the latest message on top.
   */
  private void insertStringToConsole(List<String> console, String msg) {
    if (msg != null) {
      console.add(0, msg);
      if (console.size() > Config.MAX_LIST_ITEMS) {
        console.remove(console.size() - 1);
      }
    }
  }

  /**
   * Adds a string to the corresponding console depending on whether the result is a user
   * measurement or a system measurement
   */
  private void updateResultsConsole(Intent intent) {
    int priority =
        intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD,
            MeasurementTask.INVALID_PRIORITY);
    String msg = intent.getStringExtra(UpdateIntent.STRING_PAYLOAD);
    if (msg == null) {
      // Pull out error string instead
      msg = intent.getStringExtra(UpdateIntent.ERROR_STRING_PAYLOAD);
    }
    if (msg != null) {
      if (priority == MeasurementTask.USER_PRIORITY) {
        insertStringToConsole(userResults, msg);
      } else if (priority != MeasurementTask.INVALID_PRIORITY) {
        insertStringToConsole(systemResults, msg);
      }
    }
  }

  /**
   * Return a read-only list of the user results.
   */
  public synchronized List<String> getUserResults() {
    return Collections.unmodifiableList(userResults);
  }

  /**
   * Return a read-only list of the system results.
   */
  public synchronized List<String> getSystemResults() {
    return Collections.unmodifiableList(systemResults);
  }

  /**
   * Return a read-only list of the system console messages.
   */
  public synchronized List<String> getSystemConsole() {
    return Collections.unmodifiableList(systemConsole);
  }
}

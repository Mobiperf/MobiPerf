// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import com.google.myjson.reflect.TypeToken;
import com.google.wireless.speed.speedometer.BatteryCapPowerManager.PowerAwareTask;
import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;
import com.google.wireless.speed.speedometer.util.RuntimeUtil;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * The single scheduler thread that monitors the task queue, runs tasks at their specified
 * times, and finally retrieves and reports results once they finish. 
 * 
 * All method invocations on the singleton object are thread-safe.
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
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
  
  private PendingIntent checkinIntentSender;
  /** 
   * Intent for checkin retries. Reusing checkinIntentSender for retries will cancel any
   * previously configured periodic checkin schedule. Thus we need a separate intent sender */
  private PendingIntent checkinRetryIntentSender;
  private PendingIntent measurementIntentSender;
  private AlarmManager alarmManager;
  private BatteryCapPowerManager powerManager;
  // TODO(Wenjie): add capacity control to the two queues.
  /* Both taskQueue and pendingTasks are thread safe and operations on them are atomic. 
   * To guarantee reliable value propagation between threads, use volatile keyword.
   */
  private volatile PriorityBlockingQueue<MeasurementTask> taskQueue;
  private volatile
      ConcurrentHashMap<MeasurementTask, Future<MeasurementResult>> pendingTasks;
  // Binder given to clients
  private final IBinder binder = new SchedulerBinder();
      
  private MeasurementTask currentTask;
  
  private NotificationManager notificationManager;
  private int completedMeasurementCnt = 0;
  
  /** The ArrayAdapter that stores the results of user measurements. Persisted upon app exit. */
  public ArrayAdapter<String> userResults;
  /** The ArrayAdapter that stores the results of system measurements. Persisted upon app exit. */
  public ArrayAdapter<String> systemResults;
  /** The ArrayAdapter that stores the content of the system console. Persisted upon app exit. */
  public ArrayAdapter<String> systemConsole;
  
  /**
   * The Binder class that returns an instance of running scheduler 
   */
  public class SchedulerBinder extends Binder {
    public MeasurementScheduler getService() {
      return MeasurementScheduler.this;
    }
  }

  /* Returns a IBinder that contains the instance of the MeasurementScheduler object
   * @see android.app.Service#onBind(android.content.Intent)
   */
  @Override
  public IBinder onBind(Intent intent) {
    return this.binder;
  }
  
  // Service objects are by nature singletons enforced by Android
  @Override
  public void onCreate() {
    RuntimeUtil.setContext(this.getApplicationContext());
    PhoneUtils.setGlobalContext(this.getApplicationContext());
    PhoneUtils.getPhoneUtils().registerSignalStrengthListener();
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
    
    this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    this.alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
    this.powerManager = new BatteryCapPowerManager(Config.DEFAULT_BATTERY_THRESH_PRECENT,
        Config.DEFAULT_MEASURE_WHEN_CHARGE, this);
    // Register activity specific BroadcastReceiver here    
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.PREFERENCE_ACTION);
    filter.addAction(UpdateIntent.MSG_ACTION);
    filter.addAction(UpdateIntent.CHECKIN_ACTION);
    filter.addAction(UpdateIntent.CHECKIN_RETRY_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_ACTION);
    filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
    
    broadcastReceiver = new BroadcastReceiver() {
      // If preferences are changed by the user, the scheduler will receive the update 
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(UpdateIntent.PREFERENCE_ACTION)) {
          updateFromPreference();
        } else if (intent.getAction().equals(UpdateIntent.CHECKIN_ACTION) ||
              intent.getAction().equals(UpdateIntent.CHECKIN_RETRY_ACTION)) {
          Log.d(SpeedometerApp.TAG, "Checkin intent received");
          handleCheckin();
        } else if (intent.getAction().equals(UpdateIntent.MEASUREMENT_ACTION)) {
          Log.d(SpeedometerApp.TAG, "MeasurementIntent intent received");
          handleMeasurement();
        } else if (intent.getAction().equals(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION)) {
          Log.d(SpeedometerApp.TAG, "MeasurementIntent update intent received");
          if (intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD, Config.INVALID_PROGRESS) == 
              Config.MEASUREMENT_END_PROGRESS) {
            completedMeasurementCnt++;
            updateNotificationBar();
            updateResultsConsole(intent);
          }
        } else if (intent.getAction().equals(UpdateIntent.MSG_ACTION)) {
          String msg = intent.getExtras().getString(UpdateIntent.STRING_PAYLOAD);
          insertStringToConsole(systemConsole, msg);
        }
      }
    };
    this.registerReceiver(broadcastReceiver, filter);
    
    initializeConsoles();
    startSpeedomterInForeGround();
  }
  
  public boolean hasBatteryToScheduleExperiment() {
    return powerManager.canScheduleExperiment();
  }
  
  private void startSpeedomterInForeGround() {
    //The intent to launch when the user clicks the expanded notification
    Intent intent = new Intent(this, SpeedometerApp.class);
    PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, 
        PendingIntent.FLAG_CANCEL_CURRENT);

    //This constructor is deprecated in 3.x. But most phones still run 2.x systems
    Notification notice = new Notification(R.drawable.icon, 
        getString(R.string.notificationSchedulerStarted), System.currentTimeMillis());

    //This is deprecated in 3.x. But most phones still run 2.x systems
    notice.setLatestEventInfo(this, "Speedometer", 
        getString(R.string.notificatioContent), pendIntent);

    //Put scheduler service into foreground. Makes the process less likely of being killed
    startForeground(NOTIFICATION_ID, notice);
  }
  
  private void handleCheckin() {
    if (isPauseRequested() || !powerManager.canScheduleExperiment()) {
      return;
    }
    /* The CPU can go back to sleep immediately after onReceive() returns. Acquire
     * the wake lock for the new thread here and release the lock when the thread finishes
     */
    PhoneUtils.getPhoneUtils().acquireWakeLock();
    new Thread(checkinTask).start();
  }
  
  private void handleMeasurement() {
    try {
      MeasurementTask task = taskQueue.peek();
      /* Process the head of the queue. If the count of the head task is greater than 0, 
       * we make a clone of it with the next start time and add the clone to taskQueue.
       */
      if (task != null && task.timeFromExecution() <= 0) {
        taskQueue.poll();
        Future<MeasurementResult> future;
        Log.i(SpeedometerApp.TAG, "Processing task " + task.toString());
        // Run the head task using the executor
        if (task.getDescription().priority == MeasurementTask.USER_PRIORITY) {
          sendStringMsg("***** USER_TASK *****");
          // User task can override the power policy. So a different task wrapper is used.
          future = measurementExecutor.submit(new UserMeasurementTask(task));
        } else {
          future = measurementExecutor.submit(new PowerAwareTask(task, powerManager, this));
        }
        synchronized (pendingTasks) {
          pendingTasks.put(task, future);
        }
        
        MeasurementDesc desc = task.getDescription();
        long newStartTime = desc.startTime.getTime() + (long) desc.intervalSec * 1000;
        // Add a clone with the new start time into taskQueue if count is INFINITE_COUNT or
        // desc.count is greater than one and that the task has not expired.
        if (desc.count == MeasurementTask.INFINITE_COUNT || 
            (desc.count > 1 && newStartTime < desc.endTime.getTime())) {
          MeasurementTask newTask = task.clone();
          if (desc.count != MeasurementTask.INFINITE_COUNT) {
            newTask.getDescription().count--;
          }
          newTask.getDescription().startTime.setTime(newStartTime);
          submitTask(newTask);
        }
      }
      // Schedule for the next experiment in taskQueue
      task = taskQueue.peek();
      if (task != null) {
        long timeFromExecution = Math.max(task.timeFromExecution(),
            Config.MIN_TIME_BETWEEN_MEASUREMENT_ALARM_MSEC);
        measurementIntentSender = PendingIntent.getBroadcast(this, 0, 
            new UpdateIntent("", UpdateIntent.MEASUREMENT_ACTION), 
            PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager.set(AlarmManager.RTC_WAKEUP, 
            System.currentTimeMillis() + timeFromExecution, 
            measurementIntentSender);
      }
    } catch (IllegalArgumentException e) {
      // Task creation in clone can create this exception
      Log.e(SpeedometerApp.TAG, "Exception when clonig objects");
    } catch (Exception e) {
      // We don't want any unexpected exception to crash the process
      Log.e(SpeedometerApp.TAG, "Exception when handling measurements", e);
    }
  }
  
  /** Sets the current task being run. In the current implementation, the
   * synchronized keyword is not needed because only one thread runs
   * measurements and calls this method. It is not thread safe.
   */
  public void setCurrentTask(MeasurementTask task) {
    this.currentTask = task;
  }
  
  /** Returns the current task being run. In the current implementation, the
   * synchronized keyword is not needed because only one thread runs
   * measurements and calls this method. It is not thread safe.
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
  public int onStartCommand(Intent intent, int flags, int startId)  {
    // Start up the thread running the service. Using one single thread for all requests
    Log.i(SpeedometerApp.TAG, "starting scheduler");
    if (!isSchedulerStarted) {
      updateFromPreference();
      this.resume();
      /* There is no onStop() for services. The service is only stopped when the user exists the
       * application. So don't worry about setting isSchedulerStarted to false.*/
      isSchedulerStarted = true;
    }
    return START_STICKY;
  }
  
  @Override
  public void onDestroy() {
    super.onDestroy();
    cleanUp();
  }
  
  /**
   * Returns the power manager used by the scheduler
   * */
  public BatteryCapPowerManager getPowerManager() {
    return this.powerManager;
  }
  
  /** Set the interval for checkin in seconds */
  public synchronized void setCheckinInterval(long interval) {
    this.checkinIntervalSec = Math.max(Config.MIN_CHECKIN_INTERVAL_SEC, interval);
    // the new checkin schedule will start in PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC seconds
    checkinIntentSender = PendingIntent.getBroadcast(this, 0, 
        new UpdateIntent("", UpdateIntent.CHECKIN_ACTION), PendingIntent.FLAG_CANCEL_CURRENT);
    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, 
        System.currentTimeMillis() + Config.PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC, 
        checkinIntervalSec * 1000, checkinIntentSender);
    
    Log.i(SpeedometerApp.TAG, "Setting checkin interval to " + interval + " seconds");
  }
  
  /** Returns the checkin interval of the scheduler in seconds */
  public synchronized long getCheckinInterval() {
    return this.checkinIntervalSec;
  }
  
  /** Prevents new tasks from being scheduled. All scheduled tasks will still run 
   * TODO(Wenjie): Implement a call back in the MeasurementTask to indicate a task that
   * is being run. Remove all scheduled but not yet started tasks from the executor.
   * */
  public synchronized void pause() {
    this.pauseRequested = true;
    refreshSystemStatusBar();
    updateNotificationBar();
  }
  
  /** Enables new tasks to be scheduled */
  public synchronized void resume() {
    this.pauseRequested = false;
    refreshSystemStatusBar();
    updateNotificationBar();
    this.notify(); 
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
    this.stopRequested = true;
    this.notifyAll();
    this.cleanUp();
  }
  
  /** Submit a MeasurementTask to the scheduler. Caller of this method can broadcast
   * an intent with MEASUREMENT_ACTION to start the measurement immediately.*/
  public boolean submitTask(MeasurementTask task) {
    try {
      // Immediately handles measurements created by user
      if (task.getDescription().priority == MeasurementTask.USER_PRIORITY) {
        return this.taskQueue.add(task);
      }
      
      if (taskQueue.size() >= Config.MAX_TASK_QUEUE_SIZE ||
          pendingTasks.size() >= Config.MAX_TASK_QUEUE_SIZE) {
        return false;
      }
      //Automatically notifies the scheduler waiting on taskQueue.take()
      return this.taskQueue.add(task);
    } catch (NullPointerException e) {
      Log.e(SpeedometerApp.TAG, "The task to be added is null");
      return false;
    } catch (ClassCastException e) {
      Log.e(SpeedometerApp.TAG, "cannot compare this task against existing ones");
      return false;
    } finally {
      updateNotificationBar();
    }
  }
  
  private void updateNotificationBar() {
    //The intent to launch when the user clicks the expanded notification
    Intent intent = new Intent(this, SpeedometerApp.class);
    PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, 
        PendingIntent.FLAG_CANCEL_CURRENT);

    //This constructor is deprecated in 3.x. But most phones still run 2.x systems
    Notification notice = new Notification(R.drawable.icon, 
        getString(R.string.notificationSchedulerStarted), System.currentTimeMillis());

    String notificationContent;
    if (!powerManager.canScheduleExperiment()) {
      notificationContent = "Battery is below threshold";
    } else if (isPauseRequested()) {
      notificationContent = "Speedometer is paused";
    } else {
      notificationContent = "Finished:" + completedMeasurementCnt;
      notificationContent += " Pending:" + taskQueue.size();
    }
    //This is deprecated in 3.x. But most phones still run 2.x systems
    notice.setLatestEventInfo(this, "Speedometer", 
        notificationContent, pendIntent);

    notificationManager.notify(NOTIFICATION_ID, notice);
  }

  /**
   * Always call this method to ensure the system status bar is consistent with
   * the system state. It is best to rely only on a single entity, the scheduler, to decide what 
   * should be printed. Here we update both the system status bar and the notification bar.
   */
  public void refreshSystemStatusBar() {
    Intent intent = new Intent();
    intent.setAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
    sendBroadcast(intent);
    updateNotificationBar();
  }
  
  private void updateFromPreference() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    try {
      powerManager.setBatteryThresh(Integer.parseInt(
          prefs.getString(getString(R.string.batteryMinThresPrefKey),
          String.valueOf(Config.DEFAULT_BATTERY_THRESH_PRECENT))));
      powerManager.setMeasureWhenCharging(
          prefs.getBoolean(getString(R.string.measureWhenPluggedPrefKey), 
          Config.DEFAULT_MEASURE_WHEN_CHARGE));
      
      this.setCheckinInterval(Integer.parseInt(
          prefs.getString(getString(R.string.checkinIntervalPrefKey),
          String.valueOf(Config.DEFAULT_CHECKIN_INTERVAL_SEC / 3600))) * 3600);
      
      refreshSystemStatusBar();
      
      Log.i(SpeedometerApp.TAG, "Preference set from SharedPreference: " + 
          "checkinInterval=" + checkinIntervalSec +
          ", minBatThres= " + powerManager.getBatteryThresh());
    } catch (ClassCastException e) {
      Log.e(SpeedometerApp.TAG, "exception when casting preference values", e);
    }
    // TODO(Wenjie): Add code to deal with minBatThres, measureWhenPlugged, and startOnBoot.
  }
  
  //Place holder to receive message from the UI thread
  private class UpdateHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      
    }
  }
  
  private void sendStringMsg(String str) {
    UpdateIntent intent = new UpdateIntent(str, UpdateIntent.MSG_ACTION);
    this.sendBroadcast(intent);    
  }
  
  private synchronized void cleanUp() {
    this.taskQueue.clear();
    // remove all future tasks
    this.measurementExecutor.shutdown();
    // remove and stop all active tasks
    this.measurementExecutor.shutdownNow();
    this.checkin.shutDown();
    
    this.unregisterReceiver(broadcastReceiver);
    Log.i(SpeedometerApp.TAG, "canceling pending intents");

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
    
    
    this.notifyAll();
    PhoneUtils.getPhoneUtils().shutDown();
    this.stopForeground(true);
    this.stopSelf();
    
    saveConsoleContent(systemResults, Config.PREF_KEY_SYSTEM_RESULTS);
    saveConsoleContent(userResults, Config.PREF_KEY_USER_RESULTS);
    saveConsoleContent(systemConsole, Config.PREF_KEY_SYSTEM_CONSOLE);
    
    Log.i(SpeedometerApp.TAG, "Shut down all executors and stopping service");
  }
  
  private void resetCheckin() {
    // reset counters for checkin
    checkinRetryCnt = 0;
    checkinRetryIntervalSec = Config.MIN_CHECKIN_RETRY_INTERVAL_SEC;
    checkin.initializeAccountSelector();
  }
  
  private void getTasksFromServer() throws IOException {
    Log.i(SpeedometerApp.TAG, "Downloading tasks from the server");
    checkin.getCookie();
    List<MeasurementTask> tasksFromServer = checkin.checkin();
    // The new task schedule overrides the old one
    removeAllUnscheduledTasks();

    for (MeasurementTask task : tasksFromServer) {
      Log.i(SpeedometerApp.TAG, "added task: " + task.toString());
      sendStringMsg("Adding to task queue " + task.toString());
      this.submitTask(task);
    }
  }
  
  @SuppressWarnings("unchecked")
  private void uploadResults() {
    Vector<MeasurementResult> finishedTasks = new Vector<MeasurementResult>();
    MeasurementResult result;
    Future<MeasurementResult> future;
    
    synchronized (this.pendingTasks) {
      try {
        for (MeasurementTask task : this.pendingTasks.keySet()) {
          future = this.pendingTasks.get(task);
          if (future != null) {
            if (future.isDone()) {
              try {
                this.pendingTasks.remove(task);
                if (!future.isCancelled()) {
                  result = future.get();
                  finishedTasks.add(result);
                } else {
                  finishedTasks.add(this.getFailureResult(task));
                }
              } catch (InterruptedException e) {
                /*
                 * Since the task is done, we should not need to wait anymore to get
                 * result. So we simply assume something bad happens and we return a
                 * failure result
                 */
                Log.e(SpeedometerApp.TAG, e.getMessage());
              } catch (ExecutionException e) {
                finishedTasks.add(this.getFailureResult(task));
                Log.e(SpeedometerApp.TAG, e.getMessage());
              } catch (CancellationException e) {
                Log.e(SpeedometerApp.TAG, e.getMessage());
              }
            } else if (task.isPassedDeadline()) {
              /* If a task has reached its deadline but has not been run, 
               * remove it and report failure 
               */
              this.pendingTasks.remove(task);
              future.cancel(true);
              finishedTasks.add(this.getFailureResult(task));
            }
          }
            
          if (future == null) {
            /* Tasks that are scheduled after deadline are put into pendingTasks with a 
             * null future.
             */
            this.pendingTasks.remove(task);
            finishedTasks.add(this.getFailureResult(task));
          }
        }
      } catch (ConcurrentModificationException e) {
        /* keySet is a synchronized view of the keys. However, changes during iteration will throw
         * ConcurrentModificationException. Since we have synchronized all changes to pendingTasks
         * this should not happen. 
         */
        Log.e(SpeedometerApp.TAG, "Pending tasks is changed during measurement upload");
      }
    }
    
    if (finishedTasks.size() > 0) {
      try {
        this.checkin.uploadMeasurementResult(finishedTasks);
      } catch (IOException e) {
        Log.e(SpeedometerApp.TAG, "Error when uploading message");
      }
    }
    
    Log.i(SpeedometerApp.TAG, "A total of " + finishedTasks.size() + " uploaded");
    Log.i(SpeedometerApp.TAG, "A total of " + this.pendingTasks.size() + " is in pendingTasks");
  }
  
  private class CheckinTask implements Runnable {
    @Override
    public void run() {
      Log.i(SpeedometerApp.TAG, "checking Speedometer service for new tasks");
      sendStringMsg("checkin at " + Calendar.getInstance().getTime());
      try {
        uploadResults();
        getTasksFromServer();
        // Also reset checkin if we get a success
        resetCheckin();
        // Schedule the new expeirments
        handleMeasurement();
      } catch (Exception e) {
        /*
         * Executor stops all subsequent execution of a periodic task if a raised
         * exception is uncaught. We catch all undeclared exceptions here
         */
        Log.e(SpeedometerApp.TAG, "Unexpected exceptions caught", e);
        if (checkinRetryCnt > Config.MAX_CHECKIN_RETRY_COUNT) {
          /* If we have retried more than MAX_CHECKIN_RETRY_COUNT times upon a checkin failure, 
           * we will stop retrying and wait until the next checkin period*/
          resetCheckin();
        } else if (checkinRetryIntervalSec < checkinIntervalSec) {
          Log.i(SpeedometerApp.TAG, "Retrying checkin in " + checkinRetryIntervalSec + " seconds");
          /* Use checkinRetryIntentSender so that the periodic checkin schedule will
           * remain intact
           */
          checkinRetryIntentSender = PendingIntent.getBroadcast(MeasurementScheduler.this, 0, 
              new UpdateIntent("", UpdateIntent.CHECKIN_RETRY_ACTION), 
              PendingIntent.FLAG_CANCEL_CURRENT); 
          alarmManager.set(AlarmManager.RTC_WAKEUP, 
              System.currentTimeMillis() + checkinRetryIntervalSec * 1000, 
              checkinRetryIntentSender);
          checkinRetryCnt++;
          checkinRetryIntervalSec =
              Math.min(Config.MAX_CHECKIN_RETRY_INTERVAL_SEC, checkinRetryIntervalSec * 2);
        }
      } finally {
        PhoneUtils.getPhoneUtils().releaseWakeLock();
        // Otherwise, we simply wait for the next checkin period since it's shorter than the
        // retry interval
      }
    }
  }
  
  private synchronized boolean isStopRequested() {
    return this.stopRequested;
  }
  
  private MeasurementResult getFailureResult(MeasurementTask task) {
    return new MeasurementResult(RuntimeUtil.getDeviceInfo().deviceId, 
      RuntimeUtil.getDeviceProperty(), task.getType(), Calendar.getInstance().getTime(), 
      false, task.measurementDesc);
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
      intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.USER_PRIORITY);
      MeasurementScheduler.this.sendBroadcast(intent);
      
      intent.setAction(UpdateIntent.SYSTEM_STATUS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.STATUS_MSG_PAYLOAD, realTask.getDescriptor() +
          " is running. " + (realTask.getDescription().count - 1) + " more to run.");
      
      MeasurementScheduler.this.sendBroadcast(intent);
    }
    
    private void broadcastMeasurementEnd(MeasurementResult result) {
      Intent intent = new Intent();
      intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
      intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.USER_PRIORITY);
      // A progress value greater than max progress to indicate the termination of a measurement
      intent.putExtra(UpdateIntent.PROGRESS_PAYLOAD, Config.MEASUREMENT_END_PROGRESS);
      
      if (result != null) {
        intent.putExtra(UpdateIntent.STRING_PAYLOAD, result.toString());
      } else {
        String errorString = "Measurement " + realTask.getDescriptor() + " has failed";
        errorString += "\nTimestamp: " + Calendar.getInstance().getTime();
        intent.putExtra(UpdateIntent.STRING_PAYLOAD, errorString);
      }
      MeasurementScheduler.this.sendBroadcast(intent);
      
      // Update the status bar if this is the last of the list of measurements the user
      // has scheduled
      if (realTask.measurementDesc.count == 1) {
        refreshSystemStatusBar();
      }
    }
    
    /**
     * The call() method that broadcast intents before the measurement starts and after the
     * measurement finishes.
     */
    @Override
    public MeasurementResult call() throws MeasurementError{
      MeasurementResult result = null;
      try {
        PhoneUtils.getPhoneUtils().acquireWakeLock();
        setCurrentTask(realTask);
        broadcastMeasurementStart();
        result = realTask.call();
      } finally {
        setCurrentTask(null);
        broadcastMeasurementEnd(result);
        PhoneUtils.getPhoneUtils().releaseWakeLock();
      }
      return result;
    }
  }
  
  /**
   * Persists the content of the console as a JSON string
   */
  private void saveConsoleContent(ArrayAdapter<String> consoleContent, String prefKey) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    SharedPreferences.Editor editor = prefs.edit();

    int length = consoleContent.getCount();
    ArrayList<String> items = new ArrayList<String>();
    // Since we use insertToConsole later on to restore the content, we have to store them
    // in the reverse order to maintain the same look
    for (int i = length - 1; i >= 0; i--) {
      items.add(consoleContent.getItem(i));
    }
    Type listType = new TypeToken<ArrayList<String>>(){}.getType();
    editor.putString(prefKey, MeasurementJsonConvertor.getGsonInstance().toJson(items, listType));
    editor.commit();
  }
  
  /**
   * Restores the console content from the saved JSON string
   */
  private void initializeConsoles() {
    userResults = new ArrayAdapter<String>(this, R.layout.list_item);
    systemResults = new ArrayAdapter<String>(this, R.layout.list_item);
    systemConsole = new ArrayAdapter<String>(this, R.layout.list_item);
    
    restoreConsole(systemResults, Config.PREF_KEY_SYSTEM_RESULTS);
    restoreConsole(userResults, Config.PREF_KEY_USER_RESULTS);
    restoreConsole(systemConsole, Config.PREF_KEY_SYSTEM_CONSOLE);
  }
  
  /**
   * Restores content for consoleContent with the key prefKey
   */
  private void restoreConsole(ArrayAdapter<String> consoleContent, String prefKey) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String savedConsole = prefs.getString(prefKey, null);
    if (savedConsole != null) {
      Type listType = new TypeToken<ArrayList<String>>(){}.getType();
      ArrayList<String> items = MeasurementJsonConvertor.getGsonInstance().fromJson(savedConsole, 
          listType);
      if (items != null) {
        for (String item : items) {
          insertStringToConsole(consoleContent, item);
        }
      }
    }
  }

  /**
   * Inserts a string into the console with the latest message on top
   */
  private void insertStringToConsole(ArrayAdapter<String> adapter, String msg) {
    if (msg != null) {
      adapter.insert(msg, 0);
      if (adapter.getCount() > Config.MAX_LIST_ITEMS) {
        adapter.remove(adapter.getItem(adapter.getCount() - 1));
      }
    }
  }
  
  /**
   * Adds a string to the corresponding console depending on whether the result is a 
   * user measurement or a system measurement
   */
  private void updateResultsConsole(Intent intent) {
    intent.hasExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD);
    int priority = intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, 
        MeasurementTask.INVALID_PRIORITY);
    String msg = intent.getStringExtra(UpdateIntent.STRING_PAYLOAD);
    if (msg != null) {
      if (priority == MeasurementTask.USER_PRIORITY) {
        insertStringToConsole(userResults, msg);
      } else if (priority != MeasurementTask.INVALID_PRIORITY) {
        insertStringToConsole(systemResults, msg);
      }
    }
  }
}

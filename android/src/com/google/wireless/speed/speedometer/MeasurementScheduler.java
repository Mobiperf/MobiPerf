// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.BatteryCapPowerManager.PowerAwareTask;
import com.google.wireless.speed.speedometer.util.RuntimeUtil;

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

import java.io.IOException;
import java.util.Calendar;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The single scheduler thread that monitors the task queue, runs tasks at their specified
 * times, and finally retrieves and reports results once they finish. 
 * 
 * All method invocations on the singleton object are thread-safe.
 * 
 * @author wenjiezeng@google.com (Steve Zeng)
 */
public class MeasurementScheduler extends Service {
  
  private ScheduledThreadPoolExecutor measurementExecutor;
  private BroadcastReceiver broadcastReceiver;
  private Boolean pauseRequested = true;
  private boolean stopRequested = false;
  private boolean isCheckinEnabled = Config.DEFAULT_CHECKIN_ENABLED;
  private Checkin checkin;
  private long checkinIntervalSec;
  private long checkinRetryIntervalSec;
  private int checkinRetryCnt;
  private ScheduledFuture<?> checkinFuture;
  private CheckinTask checkinTask;
  
  private BatteryCapPowerManager powerManager;
  // TODO(Wenjie): add capacity control to the two queues.
  /* Both taskQueue and pendingTasks are thread safe and operations on them are atomic. 
   * To guarantee reliable value propagation between threads, use volatile keyword.
   */
  private volatile PriorityBlockingQueue<MeasurementTask> taskQueue;
  private volatile
      ConcurrentHashMap<MeasurementTask, ScheduledFuture<MeasurementResult>> pendingTasks;
  private ScheduledExecutorService checkinExecutor;
  private SchedulerThread schedulerThread = null;
  // Binder given to clients
  private final IBinder binder = new SchedulerBinder();
      
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
    PhoneUtils.setGlobalContext(this.getApplicationContext());
    this.checkin = new Checkin(this);
    this.checkinFuture = null;
    this.checkinRetryIntervalSec = Config.MIN_CHECKIN_RETRY_INTERVAL_SEC;
    this.checkinRetryCnt = 0;
    this.checkinTask = new CheckinTask();
    this.checkinExecutor = Executors.newScheduledThreadPool(1);
    
    this.pauseRequested = true;
    this.stopRequested = false;
    
    this.measurementExecutor = new ScheduledThreadPoolExecutor(Config.THREAD_POOL_SIZE);
    this.measurementExecutor.setMaximumPoolSize(Config.THREAD_POOL_SIZE);
    this.taskQueue =
        new PriorityBlockingQueue<MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE, 
            new TaskComparator());
    this.pendingTasks =
        new ConcurrentHashMap<MeasurementTask, ScheduledFuture<MeasurementResult>>();
    
    this.powerManager = new BatteryCapPowerManager(Config.DEFAULT_BATTERY_THRESH_PRECENT, this);
    // Register activity specific BroadcastReceiver here    
    IntentFilter filter = new IntentFilter();
    filter.addAction(UpdateIntent.PREFERENCE_ACTION);
    filter.addAction(UpdateIntent.MSG_ACTION);
    broadcastReceiver = new BroadcastReceiver() {
      // If preferences are changed by the user, the scheduler will receive the update 
      @Override
      public void onReceive(Context context, Intent intent) {
        if (intent.getAction().compareToIgnoreCase(UpdateIntent.PREFERENCE_ACTION) == 0) {
          updateFromPreference();
        }
      }
    };
    this.registerReceiver(broadcastReceiver, filter);
    
    updateFromPreference();
  }
  
  @Override 
  public int onStartCommand(Intent intent, int flags, int startId)  {
    // Start up the thread running the service. Using one single thread for all requests
    if (this.schedulerThread == null) {
      Log.i(SpeedometerApp.TAG, "starting a new scheduler thread");
      this.setCheckinInterval(checkinIntervalSec);
      this.schedulerThread = new SchedulerThread();
      new Thread(this.schedulerThread).start();
      this.resume();
      this.setIsCheckinEnabled(true);
    }
    return START_STICKY;
  }
  
  /**
   * Returns the power manager used by the scheduler
   * */
  public BatteryCapPowerManager getPowerManager() {
    return this.powerManager;
  }
      
  /** Check-in is by-default disabled. SpeedometerApp will enable it. 
   *  Users can request to stop check-in altogether */
  public synchronized void setIsCheckinEnabled(boolean val) {
    this.isCheckinEnabled = val;
  }
  
  /** Returns whether auto checkin is enabled at the scheduler */
  public synchronized boolean getIsCheckinEnabled() {
    return isCheckinEnabled;
  }
  
  /** Set the interval for checkin in seconds */
  public synchronized void setCheckinInterval(long interval) {
    this.checkinIntervalSec = interval;
    if (this.checkinFuture != null) {
      this.checkinFuture.cancel(true);
      // the new checkin schedule will start in PAUSE_BETWEEN_CHECKIN_CHANGE_SEC seconds
      this.checkinFuture = checkinExecutor.scheduleAtFixedRate(this.checkinTask, 
          Config.PAUSE_BETWEEN_CHECKIN_CHANGE_SEC, this.checkinIntervalSec, TimeUnit.SECONDS);
      Log.i(SpeedometerApp.TAG, "Setting checkin interval to " + interval + " seconds");
    }
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
  }
  
  /** Enables new tasks to be scheduled */
  public synchronized void resume() {
    this.pauseRequested = false;
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
  
  /** Submit a MeasurementTask to the scheduler */
  public boolean submitTask(MeasurementTask task) {
    try {
      //Automatically notifies the scheduler waiting on taskQueue.take()
      return this.taskQueue.add(task);
    } catch (NullPointerException e) {
      Log.e(SpeedometerApp.TAG, "The task to be added is null");
      return false;
    } catch (ClassCastException e) {
      Log.e(SpeedometerApp.TAG, "cannot compare this task against existing ones");
      return false;
    }
  }
  
  private void updateFromPreference() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    try {
      this.setIsCheckinEnabled(prefs.getBoolean(
          getString(R.string.checkinEnabledPrefKey), Config.DEFAULT_CHECKIN_ENABLED));
      // The user sets checkin interval in the unit of hours
      this.setCheckinInterval(Integer.parseInt(
          prefs.getString(getString(R.string.checkinIntervalPrefKey),
          String.valueOf(Config.DEFAULT_CHECKIN_INTERVAL_SEC / 3600))) * 3600);
      powerManager.setBatteryThresh(Integer.parseInt(
          prefs.getString(getString(R.string.batteryMinThresPrefKey),
          String.valueOf(Config.DEFAULT_BATTERY_THRESH_PRECENT))));
      Log.i(SpeedometerApp.TAG, "Preference set from SharedPreference: isCheckinEnabled=" + 
          isCheckinEnabled + ", checkinInterval=" + checkinIntervalSec + 
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
    this.checkinExecutor.shutdown();
    this.checkinExecutor.shutdownNow();
    
    this.powerManager.stop();
    this.unregisterReceiver(broadcastReceiver);
    
    this.notifyAll();
    PhoneUtils.releaseGlobalContext();
    this.stopSelf();
    Log.i(SpeedometerApp.TAG, "Shut down all executors and stopping service");
  }
  
  private void resetCheckin() {
    // reset counters for checkin
    checkinRetryCnt = 0;
    checkinRetryIntervalSec = Config.MIN_CHECKIN_RETRY_INTERVAL_SEC;
    checkin.initializeAccountSelector();
  }
  
  private void getTasksFromServer() {
    Log.i(SpeedometerApp.TAG, "Downloading tasks from the server");
    checkin.getCookie();
    try {
      List<MeasurementTask> tasksFromServer = checkin.checkin();
      
      for (MeasurementTask task : tasksFromServer) {
        Log.i(SpeedometerApp.TAG, "added task: " + task.toString());
        this.taskQueue.add(task);
      }
      // Also reset checkin if we get a success
      resetCheckin();
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, "Something gets wrong when polling tasks from the" +
          " service:" + e.getMessage());
      if (checkinRetryCnt++ > Config.MAX_CHECKIN_RETRY_COUNT) {
        /* If we have retried more than MAX_CHECKIN_RETRY_COUNT times upon a checkin failure, 
         * we will stop retrying and wait until the next checkin period*/
        resetCheckin();
      } else if (checkinRetryIntervalSec < checkinIntervalSec) {
        Log.i(SpeedometerApp.TAG, "Retrying checkin in " + checkinRetryIntervalSec + " seconds");
        checkinExecutor.schedule(checkinTask, checkinRetryIntervalSec, TimeUnit.SECONDS);
        checkinRetryIntervalSec =
            Math.min(Config.MAX_CHECKIN_RETRY_INTERVAL_SEC, checkinRetryIntervalSec * 2);
      }
      // Otherwise, we simply wait for the next checkin period since it's shorter than the
      // retry interval
    }
  }
  
  @SuppressWarnings("unchecked")
  private void uploadResults() {
    Vector<MeasurementResult> finishedTasks = new Vector<MeasurementResult>();
    MeasurementResult result;
    ScheduledFuture<MeasurementResult> future;
    
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
      try {
        if (getIsCheckinEnabled()) {
          uploadResults();
          getTasksFromServer();
        }
      } catch (Exception e) {
        /*
         * Executor stops all subsequent execution of a periodic task if a raised
         * exception is uncaught. We catch all undeclared exceptions here
         */
        Log.e(SpeedometerApp.TAG, "Unexpected exceptions caught", e);
      }
    }
  }
  
  private synchronized boolean isStopRequested() {
    return this.stopRequested;
  }
  
  private class SchedulerThread implements Runnable {
    /* Gets the next task whenever the last one finishes */
    @Override
    @SuppressWarnings("unchecked")
    public void run() {
      try {
        synchronized (MeasurementScheduler.this) {
          checkinFuture =
              checkinExecutor.scheduleAtFixedRate(checkinTask, 0L, checkinIntervalSec,
                  TimeUnit.SECONDS);
        }

        /* Loop invariant: pendingTasks always contains the scheduled tasks
         * and taskQueue contains new tasks that have not been scheduled
         */
        while (!isStopRequested()) {
          Log.i(SpeedometerApp.TAG, "Checking queue for new tasks");
          
          synchronized (MeasurementScheduler.this) {
            while (isPauseRequested()) {
              try {
                Log.i(SpeedometerApp.TAG, "User requested pause");
                MeasurementScheduler.this.wait();
              } catch (InterruptedException e) {
                Log.e(SpeedometerApp.TAG, "scheduler pause is interrupted");
              }
            }
          }
          /* Schedule the new tasks and move them from taskQueue to pendingTasks
           * 
           * TODO(Wenjie): We may also need a separate rule (taskStack) for user
           * generated tasks because users may prefer to run the latest scheduled
           * task first, which is LIFO and is different from the FIFO semantics in
           * the priority queue.
           */
          MeasurementTask task;
          try {
            while ((task = taskQueue.take()) != null) {
              Log.i(SpeedometerApp.TAG, "New task arrived. There are " + taskQueue.size()
                  + " tasks in taskQueue");
              
              ScheduledFuture<MeasurementResult> future = null;
              if (!task.isPassedDeadline()) {
                /* 'Decorates' the task with a power-aware task. task will not be executed
                 * if the power policy is not met*/
                future = measurementExecutor.schedule(new PowerAwareTask(task, powerManager), 
                    task.timeFromExecution(), TimeUnit.SECONDS);  

                Log.i(SpeedometerApp.TAG,
                    "task " + task + " will start in " + task.timeFromExecution() / 1000
                        + " seconds");
              }

              synchronized (pendingTasks) {
                pendingTasks.put(task, future);
              }
              Log.i(SpeedometerApp.TAG, "There are " + pendingTasks.size() + " in pendingTasks");
            }
          } catch (InterruptedException e) {
            Log.e(SpeedometerApp.TAG, "interrupted while waiting for new tasks");
          }
        }
      } finally {
        /*
         * either stop requested or unchecked exceptions occur. perform cleanup
         * 
         * TODO(Wenjie): If this is not a user requested stop, we should thrown
         * a exception to notify Speedometer so that it can restart the
         * scheduler thread.
         */
        cleanUp();
      }
    }
  }
  
  private MeasurementResult getFailureResult(MeasurementTask task) {
    return new MeasurementResult(RuntimeUtil.getDeviceInfo().deviceId, 
      RuntimeUtil.getDeviceProperty(), task.getType(), Calendar.getInstance().getTime(), 
      false, task.measurementDesc);
  } 
}

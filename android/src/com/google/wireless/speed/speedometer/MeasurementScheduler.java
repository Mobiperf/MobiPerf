// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.util.RuntimeUtil;

import android.os.Handler;
import android.os.Message;
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
public class MeasurementScheduler implements Runnable {

  // Default checkin interval is 30 minutes
  private static final int DEDAULT_CHECKIN_INTERVAL_SEC = 60;
  private static final long PAUSE_BETWEEN_CHECKIN_CHANGE_SEC = 2L;
  private static MeasurementScheduler singleInstance = null;
  
  private ScheduledThreadPoolExecutor executor;
  private SpeedometerApp parent;
  private Handler receiver;
  private Boolean pauseRequested = true;
  private boolean stopRequested = false;
  private boolean isCheckinEnabled = false;
  private Checkin checkin;
  private long checkinIntervalSec;
  private ScheduledFuture<?> checkinFuture;
  private CheckinTask checkinTask;
  // TODO(Wenjie): add capacity control to the two queues.
  /* Both taskQueue and pendingTasks are thread safe and operations on them are atomic. 
   * To guarantee reliable value propagation between threads, use volatile keyword.
   */
  private volatile PriorityBlockingQueue<MeasurementTask> taskQueue;
  private volatile
      ConcurrentHashMap<MeasurementTask, ScheduledFuture<MeasurementResult>> pendingTasks;
  private ScheduledExecutorService checkinExecutor;
  private ScheduledExecutorService cancelExecutor;
  

  // Singleton to enforce a single scheduler in the system
  private MeasurementScheduler(SpeedometerApp parent) {
    this.parent = parent;
    this.checkin = new Checkin(parent);
    this.checkinIntervalSec = DEDAULT_CHECKIN_INTERVAL_SEC;
    this.checkinFuture = null;
    this.checkinTask = new CheckinTask();
    this.checkinExecutor = Executors.newScheduledThreadPool(1);
    this.receiver = new UpdateHandler();
    this.executor = new ScheduledThreadPoolExecutor(Config.THREAD_POOL_SIZE);
    this.executor.setMaximumPoolSize(Config.THREAD_POOL_SIZE);
    this.taskQueue = new PriorityBlockingQueue<MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE, 
        new TaskComparator());
    this.pendingTasks = 
        new ConcurrentHashMap<MeasurementTask, ScheduledFuture<MeasurementResult>>();
    this.cancelExecutor = Executors.newScheduledThreadPool(1);
  }
  
  /** Returns the singleton instance of the MeasurementScheduler class */
  public static synchronized MeasurementScheduler getInstance(SpeedometerApp parent) {
    if (singleInstance == null) {
      singleInstance = new MeasurementScheduler(parent);
      return singleInstance;
    } else {
      return singleInstance;
    }
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
          PAUSE_BETWEEN_CHECKIN_CHANGE_SEC, this.checkinIntervalSec, TimeUnit.SECONDS);
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
  
  /** Returns the handler for inter-thread communication */
  public Handler getHandler() {
    return receiver;
  }
  
  //Place holder to receive message from the UI thread
  private class UpdateHandler extends Handler {
    @Override
    public void handleMessage(Message msg) {
      
    }
  }
  
  private void sendStringMsg(String str) {
    UpdateIntent intent = new UpdateIntent(str);
    parent.sendBroadcast(intent);    
  }
  
  private synchronized void cleanUp() {
    this.taskQueue.clear();
    // remove all future tasks
    this.executor.shutdown();
    // remove and stop all active tasks
    this.executor.shutdownNow();
    this.checkinExecutor.shutdown();
    this.checkinExecutor.shutdownNow();
    this.cancelExecutor.shutdown();
    this.cancelExecutor.shutdownNow();
    this.notifyAll();
    Log.i(SpeedometerApp.TAG, "Shut down all executors");
  }
  
  private void getTasksFromServer() {
    Log.i(SpeedometerApp.TAG, "Download tasks from the server");
    checkin.getCookie();
    try {
      List<MeasurementTask> tasksFromServer = checkin.checkin();
      
      for (MeasurementTask task : tasksFromServer) {
        Log.i(SpeedometerApp.TAG, "added task: " + task.toString());
        this.taskQueue.add(task);
      }
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, "Something gets wrong when polling tasks from the" +
          " service:" + e.getMessage());
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
          if (future != null && future.isDone()) {
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
      parent.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          if (getIsCheckinEnabled()) {
            uploadResults();
            getTasksFromServer();
          }
        }
      });
    }
  }
  
  private class CancelTask implements Runnable {
    ScheduledFuture<MeasurementResult> taskToCancel;
    
    public CancelTask(ScheduledFuture<MeasurementResult> taskToCancel) {
      this.taskToCancel = taskToCancel;
    }
    
    @Override
    public void run() {
      /* We enforce a strict deadline rule here: cancel the task even it is already running once
       * deadline has come */
      if (!this.taskToCancel.isDone()) {
        this.taskToCancel.cancel(true);
        Log.i(SpeedometerApp.TAG, "Canceling task as its deadline is reached");
      }
    }
  }
  
  private synchronized boolean isStopRequested() {
    return this.stopRequested;
  }
  
  /* Gets the next task whenever the last one finishes */
  @Override
  @SuppressWarnings("unchecked")
  public void run() {
    try {
      synchronized (this) {
        this.checkinFuture =
            checkinExecutor.scheduleAtFixedRate(this.checkinTask, 0L, this.checkinIntervalSec,
                TimeUnit.SECONDS);
      }
      
      /* Loop invariant: pendingTasks always contains the scheduled tasks
       * and taskQueue contains new tasks that have not been scheduled
       */
      while (!this.isStopRequested()) {
        Log.i(SpeedometerApp.TAG, "Checking queue for new tasks");
        if (this.isPauseRequested()) {
          synchronized (this) {
            try {
              Log.i(SpeedometerApp.TAG, "User requested pause");
              this.wait();
            } catch (InterruptedException e) {
              Log.e(SpeedometerApp.TAG, "scheduler pause is interrupted");
            }
          }
        }
        /* Schedule the new tasks and move them from taskQueu to pendingTasks
         * 
         * TODO(Wenjie): We may also need a separate rule (taskStack) for user
         * generated tasks because users may prefer to run the latest scheduled
         * task first, which is LIFO and is different from the FIFO semantics in
         * the priority queue.
         */
        MeasurementTask task;
        try {
          while (!this.isStopRequested() && (task = this.taskQueue.take()) != null) {
            Log.i(SpeedometerApp.TAG, "New task arrived. There are " + this.taskQueue.size() + 
            " tasks in taskQueue");
            ScheduledFuture<MeasurementResult> future = null;
            if (!task.isPassedDeadline()) {
              future = executor.schedule(task, task.timeFromExecution(), TimeUnit.SECONDS);
              if (task.measurementDesc.endTime != null) {
                long delay = task.measurementDesc.endTime.getTime() - System.currentTimeMillis();
                CancelTask cancelTask = new CancelTask(future);
                cancelExecutor.schedule(cancelTask, delay, TimeUnit.MILLISECONDS);
              }
  
              Log.i(SpeedometerApp.TAG, "task " + task + " will start in " + 
                  task.timeFromExecution() / 1000 + " seconds");
            }
                                   
            synchronized (this.pendingTasks) {
              assert(this.pendingTasks.keySet().contains(task) == false);
              this.pendingTasks.put(task, future);
            }
            Log.i(SpeedometerApp.TAG, "There are " + this.pendingTasks.size() + 
                " in pendingTasks");
          }
        } catch (InterruptedException e) {
          Log.e(SpeedometerApp.TAG, "interrupted while waiting for new tasks");
        }
      }
    } finally {
      /* either stop requested or unchecked exceptions occur. perform cleanup
       * 
       * TODO(Wenjie): If this is not a user requested stop, we should thrown
       * a exception to notify Speedometer so that it can restart the scheduler
       * thread.
       */
      cleanUp();
    }
  }
  
  private MeasurementResult getFailureResult(MeasurementTask task) {
    return new MeasurementResult(RuntimeUtil.getDeviceInfo().deviceId, 
      RuntimeUtil.getDeviceProperty(), task.getType(), Calendar.getInstance().getTime(), 
      false, task.measurementDesc);
  }
}

// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;
import com.google.wireless.speed.speedometer.util.RuntimeUtil;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.Calendar;
import java.util.Comparator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The single scheduler thread that monitors the task queue, runs tasks at their specified
 * times, and finally retrieves and reports results once they finish.
 * @author wenjiezeng@google.com (Steve Zeng)
 * 
 */
public class MeasurementScheduler implements Runnable {

  private ScheduledThreadPoolExecutor executor;
  private SpeedometerApp parent;
  private Handler receiver;
  private static MeasurementScheduler singleInstance = null;
  private PriorityBlockingQueue<MeasurementTask> taskQueue;
  private boolean stopRequested = false;

  // Singleton to enforce a single scheduler in the system
  private MeasurementScheduler(SpeedometerApp parent) {
    this.parent = parent;
    this.receiver = new UpdateHandler();
    this.executor = new ScheduledThreadPoolExecutor(Config.THREAD_POOL_SIZE);
    this.taskQueue = new PriorityBlockingQueue<MeasurementTask>(Config.MAX_TASK_QUEUE_SIZE, 
        new TaskComparator());
  }
  
  public static synchronized MeasurementScheduler getInstance(SpeedometerApp parent) {
    if (singleInstance == null) {
      singleInstance = new MeasurementScheduler(parent);
      return singleInstance;
    } else {
      return singleInstance;
    }
  }
  
  private class TaskComparator implements Comparator<MeasurementTask> {

    @Override
    public int compare(MeasurementTask task1, MeasurementTask task2) {
      return task1.compareTo(task2);
    }   
  }
  
  synchronized void requestStop() {
    this.stopRequested = true;
  }
  
  public boolean submitTask(MeasurementTask task) {
    try {
      return this.taskQueue.add(task);
    } catch (NullPointerException e) {
      Log.e(SpeedometerApp.TAG, "The task to be added is null");
      return false;
    } catch (ClassCastException e) {
      Log.e(SpeedometerApp.TAG, "cannot compare this task against existing ones");
      return false;
    }
  }
  
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
  
  private void cleanUp() {
    this.taskQueue.removeAll(this.taskQueue);
    // remove all future tasks
    this.executor.shutdown();
    // remove and stop all active tasks
    this.executor.shutdownNow();    
  }

  /* Checks the task queue every second for new task */
  @Override
  @SuppressWarnings("unchecked")
  public void run() {
    try {
      // TODO(Wenjie): Add looper to receive message.
      while (!this.stopRequested) {
        MeasurementTask task = this.taskQueue.peek();
        if (task != null) {        
          if (task.isPassedDeadline()) {
            // Too bad, passed deadline. Continue to get the next task if there's any
            // TODO(Wenjie): send to the server a measurement failure
            Log.i(SpeedometerApp.TAG, "deadline passed. remove task " + task.toString());
            this.taskQueue.poll();
            continue;
          }
          task = this.taskQueue.poll();
          ScheduledFuture<MeasurementResult> future;   
          // negative value in timeFromExecution indicates immediate execution
          future = executor.schedule(task, task.timeFromExecution(), TimeUnit.SECONDS);
          sendStringMsg("task " + task + " started");
          
          MeasurementResult result = null;
          try {            
            // future.get() can result in wait() for the thread that handles the Callable to finish
            result = future.get();
            sendStringMsg("Measurement succeeds");
            sendStringMsg(result.toString());
            /* TODO(Wenjie): We currently print the JSON encoded result task to the console for
             * debugging purpose. This should be removed in the release version. */ 
            sendStringMsg(MeasurementJsonConvertor.toJsonString(result));
          } catch (InterruptedException e) {
            result = this.getFailureResult(task);
            sendStringMsg("Measurement fails");
            Log.e(SpeedometerApp.TAG, e.getMessage());
          } catch (ExecutionException e) {
            result = this.getFailureResult(task);
            sendStringMsg("Measurement fails");
            Log.e(SpeedometerApp.TAG, e.getMessage());
          } finally {
            /* TODO(Wenjie): Send back the result (and construct one if
             * needed), be it a success or failure */
          }
        } else {      
          try {
            sendStringMsg("There is no ongoing measurement task.");
            Log.d(SpeedometerApp.TAG, "Empty task queue");
            synchronized (this) {
              this.wait();
            }
          } catch (InterruptedException e) {
            Log.e(SpeedometerApp.TAG, "interrupted during wait");
          }   
        }
      }
    } finally {
      // either stop requested or unchecked exceptions occur. perform cleanup
      cleanUp();
    }
  }
  
  private MeasurementResult getFailureResult(MeasurementTask task) {
    return new MeasurementResult(RuntimeUtil.getDeviceInfo().deviceId, 
      RuntimeUtil.getDeviceProperty(), task.getType(), Calendar.getInstance().getTime(), 
      false, task.measurementDesc);
  }  
}

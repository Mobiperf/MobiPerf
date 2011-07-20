package com.google.wireless.speed.speedometer;

import java.io.IOException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import android.util.Log;

/**
 * Manages the schedule for measurement and other tasks.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
public class TaskScheduler {
  
  private static final int CHECKIN_INTERVAL_SEC = 30;
  private static final int CHECK_COMPLETION_INTERVAL_SEC = 10;
  private Checkin checkin;
  private ScheduledExecutorService executor;
  private Vector<ScheduledFuture<MeasurementResult>> pending = 
    new Vector<ScheduledFuture<MeasurementResult>>();
  
  public TaskScheduler(Checkin checkin) {
    this.checkin = checkin;
  }
  
  public void start() {
    Log.i(Speedometer.TAG, "Starting TaskScheduler");
    this.executor = Executors.newScheduledThreadPool(1);
    executor.scheduleAtFixedRate(new CheckinRunnable(), 0L,
        CHECKIN_INTERVAL_SEC, TimeUnit.SECONDS);
    executor.scheduleAtFixedRate(new CheckCompletedRunnable(), 0L,
        CHECK_COMPLETION_INTERVAL_SEC, TimeUnit.SECONDS);
  }
  
  class CheckinRunnable implements Runnable {
    public void run() {
      try {
        Log.i("CheckinRunnable", "Doing checkin");
        List<MeasurementTask> newTasks = checkin.checkin();
        Log.i("CheckinRunnable", "CHECKIN DONE"); 
        Log.i(Speedometer.TAG, "Checkin got " + newTasks.size() + " tasks");
        addToSchedule(newTasks);
      } catch (IOException e) {
        Log.e(Speedometer.TAG, "Unable to perform checkin", e);
      }
    }
  }
  
  class CheckCompletedRunnable implements Runnable {
    public void run() {
      checkCompleted();
    }
  }
  
  public synchronized void addToSchedule(List<MeasurementTask> toadd) {
    try {
	    Log.i(Speedometer.TAG, "adding " + toadd.size() + " tasks to schedule");
	    for (MeasurementTask mt : toadd) {
	      ScheduledFuture<MeasurementResult> future = this.executor.schedule(
	          mt, 0, TimeUnit.MILLISECONDS);
	      pending.add(future);
	    }
    } catch (Exception e) {
      Log.w(Speedometer.TAG, e.getMessage());
    }
    // TODO(mdw): Truncate task list when entries become too old
  }
  
  public synchronized void checkCompleted() {
    Log.i(Speedometer.TAG, "checkCompleted: " + pending.size() + 
        " pending tasks");
    for (ScheduledFuture<MeasurementResult> future : pending) {
      if (future.isDone()) {
        MeasurementResult result;
        try {
          result = future.get();
    	    this.checkin.uploadMeasurementResult(result);
        } catch (InterruptedException e) {
          taskFailed(future, e);
        } catch (ExecutionException e) {
          taskFailed(future, e);
        } catch (IOException e) {
          Log.w(Speedometer.TAG, "Unable to upload result: " + Log.getStackTraceString(e));
        }
        pending.remove(future);
      }
    }
  }
  
  private void taskFailed(ScheduledFuture<MeasurementResult> future,
      Exception e) {
    Log.w(Speedometer.TAG, "Task " + future + "failed: " + e);
    // TODO(mdw): Add to log or count number of failures
  }

  public String toString() {
    return "<TaskScheduler> pending: " + pending.size();
  }

}

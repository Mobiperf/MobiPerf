package com.mobiperf.speedometer.speed;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.mobiperf.mobiperf.MobiperfActivity;
import com.mobiperf.speedometer.measurements.DnsLookupTask;
import com.mobiperf.speedometer.measurements.DnsLookupTask.DnsLookupDesc;
import com.mobiperf.speedometer.measurements.HttpTask;
import com.mobiperf.speedometer.measurements.HttpTask.HttpDesc;
import com.mobiperf.speedometer.measurements.PingTask;
import com.mobiperf.speedometer.measurements.PingTask.PingDesc;
import com.mobiperf.speedometer.measurements.TracerouteTask;
import com.mobiperf.speedometer.measurements.TracerouteTask.TracerouteDesc;

public class PeriodicTest extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle b = intent.getExtras();
		String schedtest = b.getString("test");
		
		MeasurementTask newTask = null;
		try {
			if (schedtest.equals(PingTask.TYPE) || schedtest.equals(Config.ALLTASK_TYPE)) {
				Map<String, String> params = new HashMap<String, String>();
				params.put("target", Config.DEFAULT_TEST_URL);
				PingDesc desc = new PingDesc(null, Calendar.getInstance()
						.getTime(), null,
						Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
						Config.DEFAULT_USER_MEASUREMENT_COUNT,
						MeasurementTask.USER_PRIORITY, params);
				newTask = new PingTask(desc, context.getApplicationContext());
			} 

			if (schedtest.equals(HttpTask.TYPE) || schedtest.equals(Config.ALLTASK_TYPE)) {
				Map<String, String> params = new HashMap<String, String>();
				params.put("url", Config.DEFAULT_TEST_URL);
				params.put("method", "get");
				HttpDesc desc = new HttpDesc(null, Calendar.getInstance()
						.getTime(), null,
						Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
						Config.DEFAULT_USER_MEASUREMENT_COUNT,
						MeasurementTask.USER_PRIORITY, params);
				newTask = new HttpTask(desc, context.getApplicationContext());
			}

			if (schedtest.equals(TracerouteTask.TYPE) || schedtest.equals(Config.ALLTASK_TYPE)) {
				
				Map<String, String> params = new HashMap<String, String>();
				params.put("target", Config.DEFAULT_TEST_URL);
				TracerouteDesc desc = new TracerouteDesc(null, Calendar
						.getInstance().getTime(), null,
						Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
						Config.DEFAULT_USER_MEASUREMENT_COUNT,
						MeasurementTask.USER_PRIORITY, params);
				newTask = new TracerouteTask(desc, context.getApplicationContext());
			} 

			if (schedtest.equals(DnsLookupTask.TYPE) || schedtest.equals(Config.ALLTASK_TYPE)) {
				
				Map<String, String> params = new HashMap<String, String>();
				params.put("target", Config.DEFAULT_TEST_URL);
				DnsLookupDesc desc = new DnsLookupDesc(null, Calendar
						.getInstance().getTime(), null,
						Config.DEFAULT_USER_MEASUREMENT_INTERVAL_SEC,
						Config.DEFAULT_USER_MEASUREMENT_COUNT,
						MeasurementTask.USER_PRIORITY, params);
				newTask = new DnsLookupTask(desc, context.getApplicationContext());
			}

			if (newTask != null) {
				MeasurementScheduler scheduler = MobiperfActivity.scheduler;
				if (scheduler != null && scheduler.submitTask(newTask)) {
					// Broadcast an intent with MEASUREMENT_ACTION so that
					// the scheduler will immediately
					// handles the user measurement

					context.sendBroadcast(new UpdateIntent("",
							UpdateIntent.MEASUREMENT_ACTION));
					// startActivity(new Intent(this,
					// ResultsConsole.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
					// | Intent.FLAG_ACTIVITY_CLEAR_TOP));
				}
			}

		} catch (Exception e) {
			Logger.e("Exception when creating user measurements", e);
		}
		
	}
}

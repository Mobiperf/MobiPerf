package com.mobiperf.speedometer.speed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class PeriodicTest extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle b = intent.getExtras();
		String schedtest = b.getString("test");
		
	}
}

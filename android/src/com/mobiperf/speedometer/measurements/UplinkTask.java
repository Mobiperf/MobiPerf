/* Copyright 2012 University of Michigan.
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

package com.mobiperf.speedometer.measurements;

import java.io.DataOutputStream;
import java.io.InvalidClassException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.Map;

import android.content.Context;

import com.mobiperf.speedometer.Config;
import com.mobiperf.speedometer.Logger;
import com.mobiperf.speedometer.MeasurementDesc;
import com.mobiperf.speedometer.MeasurementError;
import com.mobiperf.speedometer.MeasurementResult;
import com.mobiperf.speedometer.MeasurementTask;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;
import com.mobiperf.util.Utilities;

/**
 * A callable that executes uplink throughput test.
 * 
 * @author hjx@umich.edu (Junxian Huang)
 */
public class UplinkTask extends MeasurementTask {
	// Type name for internal use
	public static final String TYPE = "uplink_throughput";
	// Human readable name for the task
	public static final String DESCRIPTOR = "Uplink throughput";
	// Delimiter used to separate targets from target
	public static final String TARGET_DELIMITER = ";";

	/**
	 * The description of uplink throughput measurement
	 */
	public static class UplinkDesc extends MeasurementDesc {
		/** a list of servers to concurrently connect to for the uplink throughput test
		 * target may only contain 1 element, or be null
		 * seperated by TARGET_DELIMITER, e.g., "server1;server2;server3" 
		 * */
		public String target;
		//private String server;

		public UplinkDesc(String key, Date startTime, Date endTime, double intervalSec, 
				long count, long priority, Map<String, String> params) {
			super(UplinkTask.TYPE, key, startTime, endTime, intervalSec, count, priority, params);
			initalizeParams(params);
			if (this.target == null || this.target.length() == 0) {
				throw new InvalidParameterException("UplinkTask cannot be created due to null target string");
			}
		}

		@Override
		public String getType() {
			return UplinkTask.TYPE;
		}

		@Override
		protected void initalizeParams(Map<String, String> params) {
			if (params == null) {
				return;
			}
			this.target = params.get("target");
		}
	}

	public UplinkTask(MeasurementDesc desc, Context parent) {
		super(new UplinkDesc(desc.key, desc.startTime, desc.endTime,
				desc.intervalSec, desc.count, desc.priority, desc.parameters), parent);
	}

	/**
	 * Returns a copy of the UplinkTask
	 */
	@Override
	public MeasurementTask clone() {
		MeasurementDesc desc = this.measurementDesc;
		UplinkDesc newDesc = new UplinkDesc(desc.key, desc.startTime, desc.endTime, 
				desc.intervalSec, desc.count, desc.priority, desc.parameters);
		return new UplinkTask(newDesc, parent);
	}

	/**
	 * Wrapper class for running uplink throughput test, one test is a separate thread
	 */
	public static class UplinkThread extends Thread {

		//host to connect to for this measurement thread
		public String host;

		//this is shared across multiple threads and must be reset to 0 every time a new multi-threaded test starts
		//periodically read this number to estimate throughput
		public static int totalBytes;

		public UplinkThread(String host) {
			this.host = host;
		}

		public static void reset() {
			totalBytes = 0;
		}
		public synchronized static void update(int delta) {
			totalBytes += delta;
		}

		public void run() {
			Socket tcpSocket = null;
			DataOutputStream os = null;

			try {
				tcpSocket = new Socket();
				SocketAddress remoteAddr = new InetSocketAddress(host, Config.PORT_UPLINK_MLAB);
				tcpSocket.connect(remoteAddr, Config.TCP_TIMEOUT_IN_MILLI);
				os = new DataOutputStream(tcpSocket.getOutputStream());
				tcpSocket.setSoTimeout(Config.TCP_TIMEOUT_IN_MILLI);
				tcpSocket.setTcpNoDelay(true); //TODO: should this be set??
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}

			String buf = Utilities.genRandomString(Config.THROUGHPUT_UP_SEGMENT_SIZE);
			byte[] message = buf.getBytes();
			System.out.println ("------- MESSAGE LENGTH = " + message.length);
			long startTime = System.currentTimeMillis();
			long endTime = System.currentTimeMillis();

			try {
				do {
					os.write(message);
					//os.flush(); //TODO should this be called??
					endTime = System.currentTimeMillis();
					UplinkThread.update(message.length);
				} while((endTime - startTime) < Config.TP_DURATION_IN_MILLI);
			} catch ( Exception e ) {
				e.printStackTrace();
				return;
			}

			try {
				os.close();
				tcpSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public MeasurementResult call() throws MeasurementError {
		//TODO replace with uplink measurement
		UplinkDesc taskDesc = (UplinkDesc) this.measurementDesc;
		String[] targets = taskDesc.target.split(UplinkTask.TARGET_DELIMITER);
		UplinkThread.reset();
		
		//TODO ask MLab to start collecting tcpdump with a 128-bit UUID generated from xxx
		for(int i = 0 ; i < targets.length ; i++) {
			new UplinkThread(targets[i]).start();
		}

		//TODO start a timer and wait for slow start to pass
		//and then periodically read UplinkThread.totalBytes to estimate throughput
		//and write to results
		
		//TODO, once experiments finishes, make sure all UplinkThread quits
		//TODO, ask MLab to end tcpdump collection

		PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
		MeasurementResult result = new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
				phoneUtils.getDeviceProperty(), UplinkTask.TYPE,
				System.currentTimeMillis() * 1000, true, this.measurementDesc);
		//TODO use result.addResult to add all results from uplink test
		//result.addResult("address", resultInet.getHostAddress());
		Logger.i(MeasurementJsonConvertor.toJsonString(result));
		return result;
	}

	@SuppressWarnings("rawtypes")
	public static Class getDescClass() throws InvalidClassException {
		return UplinkDesc.class;
	}

	@Override
	public String getType() {
		return UplinkTask.TYPE;
	}

	@Override
	public String getDescriptor() {
		return DESCRIPTOR;
	}

	@Override
	public String toString() {
		UplinkDesc desc = (UplinkDesc) measurementDesc;
		return "[Uplink Throughput]\n  Target: " + desc.target
				+ "\n  Interval (sec): " + desc.intervalSec + "\n  Next run: " + desc.startTime;
	}

	@Override
	public void stop() {
		// There is nothing we need to do to stop the Uplink throughput measurement
		// TODO ask the MLab nodes to stop tcpdump collection
	}
}

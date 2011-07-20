package com.google.wireless.speed.speedometer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Handles checkins with the Speedometer server.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
public class Checkin {
  private Speedometer speedometer;
  private String serverUrl;
  private Date lastCheckin;
  private volatile Cookie authCookie = null;
  private AsyncTask<String, Void, Cookie> getCookieTask = null;
  private TaskScheduler scheduler;
  
  public Checkin(Speedometer speedometer, String serverUrl) {
    this.speedometer = speedometer;
    this.serverUrl = serverUrl;
    this.scheduler = new TaskScheduler(this);
    speedometer.setStatus("Server: " + this.serverUrl);
  }
  
  public Checkin(Speedometer speedometer) {
    this.speedometer = speedometer;
    this.serverUrl = speedometer.getResources().getString(
        R.string.SpeedometerServerURL);
    speedometer.setStatus("Server: " + this.serverUrl);
    this.scheduler = new TaskScheduler(this);
    scheduler.start();
  }
  
  /** Returns whether the service is running on a testing server. */
  public boolean isTestingServer() {
    if (serverUrl.indexOf("corp.google.com") > 0) {
      return true;
    } else {
      return false;
    }
  }
  
  /** Return a fake authentication cookie for a test server instance */
  private Cookie getFakeAuthCookie() {
    BasicClientCookie cookie = new BasicClientCookie(
        "dev_appserver_login",
        "test@nobody.com:False:185804764220139124118");
    cookie.setDomain(".google.com");
    cookie.setVersion(1);
    cookie.setPath("/");
    cookie.setSecure(false);
    return cookie;
  }
  
  public Date lastCheckinTime() {
    return this.lastCheckin;
  }
  
  public String getServerUrl() {
    return serverUrl;
  }
  
  public TaskScheduler getScheduler() {
    return scheduler;
  }
  
  public List<MeasurementTask> checkin() throws IOException {
    Log.i(Speedometer.TAG, "Checkin.checkin() called");
    try {
      JSONObject status = new JSONObject();
      status.put("id", Speedometer.getDeviceId());
      // TODO(mdw): Replace these with real values
      status.put("manufacturer", Build.MANUFACTURER);
      status.put("model", Build.MODEL);
      status.put("os", "Android");
      status.put("properties", Speedometer.getDeviceProperties());
      
      String result = speedometerServiceRequest("checkin", status.toString());
      Log.i(Speedometer.TAG, "Checkin result: " + result);
      
      // Parse the result
      Vector<MeasurementTask> schedule = new Vector<MeasurementTask>();
      JSONArray jsonArray = new JSONArray(result);
      Log.i(Speedometer.TAG, "Got " + jsonArray.length() + " entries in JSON array");
      for (int i = 0; i < jsonArray.length(); i++) {
        Log.i(Speedometer.TAG, "Parsing index " + i);
        JSONObject json = jsonArray.optJSONObject(i);
        Log.i(Speedometer.TAG, "Value is " + json);
        if (json != null) {
          try {
            MeasurementTask t = MeasurementTask.parseJson(json);
            schedule.add(t);
          } catch (IllegalArgumentException e) {
            Log.w(Speedometer.TAG, "Could not create task from JSON: " + e);
            // Just skip it, and try the next one
          }
        }
      }
      
      this.lastCheckin = new Date();
      Log.i(Speedometer.TAG, "Checkin complete, got " + schedule.size() +
          " new tasks");
      return schedule;
      
    } catch (Exception e) {
      Log.e(Speedometer.TAG, "Got exception during checkin: " + Log.getStackTraceString(e));
      throw new IOException(e.getMessage());
    }
  }
  
  public void uploadMeasurementResult(MeasurementResult result)
  throws IOException {
    Log.i(Speedometer.TAG, "TaskSchedule.uploadMeasurementResult() called");
    JSONObject resultJson = result.result();
    try {
      resultJson.put("device_id", Speedometer.getDeviceId());
      resultJson.put("properties", Speedometer.getDeviceProperties());
    } catch (JSONException e) {
      Log.e(Speedometer.TAG, "Unable to set device_id or properties: " + e);
    }
    
    // Currently only upload a single measurement - but API requires it to be an array
    JSONArray resultArray = new JSONArray();
    resultArray.put(resultJson);
    String response = 
      speedometerServiceRequest("postmeasurement", resultArray.toString());
    try {
      JSONObject responseJson = new JSONObject(response);
      if (!responseJson.getBoolean("success")) {
        throw new IOException("Failure posting measurement result");
      }
    } catch (JSONException e) {
      throw new IOException(e.getMessage());
    }
    Log.i(Speedometer.TAG, "TaskSchedule.uploadMeasurementResult() complete");
  }
  
  @SuppressWarnings("unused")
  private String speedometerServiceRequest(String url)
      throws ClientProtocolException, IOException {
    
    synchronized (this) {
      if (authCookie == null) {
        if (!checkGetCookie()) {
          throw new IOException("No authCookie yet");
        }
      }
    }
    
    DefaultHttpClient client = new DefaultHttpClient();
    // TODO(mdw): For some reason this is not sending the cookie to the
    // test server, probably because the cookie itself is not properly
    // initialized. Below I manually set the Cookie header instead.
    CookieStore store = new BasicCookieStore();
    store.addCookie(authCookie);
    client.setCookieStore(store);
    Log.i(Speedometer.TAG, "authCookie is: " + authCookie);
    
    String fullurl = serverUrl + "/" + url;
    HttpGet getMethod = new HttpGet(fullurl);
    // TODO(mdw): This should not be needed
    getMethod.addHeader("Cookie", authCookie.getName() + "=" + authCookie.getValue());
    
    ResponseHandler<String> responseHandler = new BasicResponseHandler();
    String result = client.execute(getMethod, responseHandler);
    return result;
  }
  
  private String speedometerServiceRequest(String url, String jsonString) 
    throws IOException {
    
    synchronized (this) {
      if (authCookie == null) {
        if (!checkGetCookie()) {
          throw new IOException("No authCookie yet");
        }
      }
    }
    
    DefaultHttpClient client = new DefaultHttpClient();
    // TODO(mdw): For some reason this is not sending the cookie to the
    // test server, probably because the cookie itself is not properly
    // initialized. Below I manually set the Cookie header instead.
    CookieStore store = new BasicCookieStore();
    store.addCookie(authCookie);
    client.setCookieStore(store);
    Log.i(Speedometer.TAG, "authCookie is: " + authCookie);
    
    String fullurl = serverUrl + "/" + url;
    HttpPost postMethod = new HttpPost(fullurl);
    
    StringEntity se;
    try {
      se = new StringEntity(jsonString);
    } catch (UnsupportedEncodingException e) {
      throw new IOException(e.getMessage());
    }
    postMethod.setEntity(se);
    postMethod.setHeader("Accept", "application/json");
    postMethod.setHeader("Content-type", "application/json");
    // TODO(mdw): This should not be needed
    postMethod.setHeader("Cookie", authCookie.getName() + "=" + authCookie.getValue());

    ResponseHandler<String> responseHandler = new BasicResponseHandler();
    String result = client.execute(postMethod, responseHandler);
    return result;
  }
  
  /**
   * Initiate process to get the authorization cookie for the user account.
   * Returns immediately.
   */
  public synchronized void getCookie() {
    if (isTestingServer()) {
      Log.i(Speedometer.TAG, "Setting fakeAuthCookie");
      authCookie = getFakeAuthCookie();
      return;
    }
    if (getCookieTask == null) {
	    try {
	      getCookieTask = new AccountSelector(speedometer, this).authorize();
	    } catch (OperationCanceledException e) {
	      Log.e(Speedometer.TAG, "Unable to get auth cookie", e);
	    } catch (AuthenticatorException e) {
	      Log.e(Speedometer.TAG, "Unable to get auth cookie", e);
	    } catch (IOException e) {
	      Log.e(Speedometer.TAG, "Unable to get auth cookie", e);
	    }
    }
  }
  
  private synchronized boolean checkGetCookie() {
    if (isTestingServer()) {
      authCookie = getFakeAuthCookie();
      return true;
    }
    if (getCookieTask == null) {
      Log.i(Speedometer.TAG, "checkGetCookie called too early");
      return false;
    }
	  if (getCookieTask.getStatus() == AsyncTask.Status.FINISHED) {
      try {
        authCookie = getCookieTask.get();
        Log.i(Speedometer.TAG, "Got authCookie: " + authCookie);
        return true;
      } catch (InterruptedException e) {
        Log.e(Speedometer.TAG, "Unable to get auth cookie", e);
        return false;
      } catch (ExecutionException e) {
        Log.e(Speedometer.TAG, "Unable to get auth cookie", e);
        return false;
      }
    } else {
      return false;
    }
  }
  

}

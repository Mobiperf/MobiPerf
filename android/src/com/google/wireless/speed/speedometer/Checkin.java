// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import com.google.wireless.speed.speedometer.util.MeasurementJsonConvertor;
import com.google.wireless.speed.speedometer.util.PhoneUtils;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.util.Log;

import org.apache.http.client.CookieStore;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Handles checkins with the SpeedometerApp server.
 * 
 * @author mdw@google.com (Matt Welsh)
 * @author wenjiezeng@google.com (Wenjie Zeng)
 */
public class Checkin {
  private static final int POST_TIMEOUT_MILLISEC = 20 * 1000;
  private Context context;
  private String serverUrl;
  private Date lastCheckin;
  private volatile Cookie authCookie = null;
  private AccountSelector accountSelector = null;
  PhoneUtils phoneUtils;
  
  public Checkin(Context context, String serverUrl) {
    phoneUtils = PhoneUtils.getPhoneUtils();
    this.context = context;
    this.serverUrl = serverUrl;
    sendStringMsg("Server: " + this.serverUrl);
  }
  
  public Checkin(Context context) {
    phoneUtils = PhoneUtils.getPhoneUtils();
    this.context = context;
    this.serverUrl = phoneUtils.getServerUrl();
    sendStringMsg("Server: " + this.serverUrl);
  }
  
  /** Returns whether the service is running on a testing server. */
  public boolean isTestingServer() {
    if (phoneUtils.isTestingServer(serverUrl)) {
      accountSelector = new AccountSelector(context, this);
      return true;
    } else {
      return false;
    }
  }
  
  /** Shuts down the checkin thread */
  public void shutDown() {
    if (this.accountSelector != null) {
      this.accountSelector.shutDown();
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
  
  public List<MeasurementTask> checkin() throws IOException {
    Log.i(SpeedometerApp.TAG, "Checkin.checkin() called");
    boolean checkinSuccess = false;
    try {
      JSONObject status = new JSONObject();
      DeviceInfo info = phoneUtils.getDeviceInfo();
      // TODO(Wenjie): There is duplicated info here, such as device ID. 
      status.put("id", info.deviceId);
      status.put("manufacturer", info.manufacturer);
      status.put("model", info.model);
      status.put("os", info.os);
      status.put("properties", 
          MeasurementJsonConvertor.encodeToJson(phoneUtils.getDeviceProperty()));
      
      Log.d(SpeedometerApp.TAG, status.toString());
      
      String result = speedometerServiceRequest("checkin", status.toString());
      Log.d(SpeedometerApp.TAG, "Checkin result: " + result);
      
      // Parse the result
      Vector<MeasurementTask> schedule = new Vector<MeasurementTask>();
      JSONArray jsonArray = new JSONArray(result);
      
      for (int i = 0; i < jsonArray.length(); i++) {
        Log.d(SpeedometerApp.TAG, "Parsing index " + i);
        JSONObject json = jsonArray.optJSONObject(i);
        Log.d(SpeedometerApp.TAG, "Value is " + json);
        if (json != null) {
          try {
            MeasurementTask task = 
                MeasurementJsonConvertor.makeMeasurementTaskFromJson(json, this.context);
            Log.i(SpeedometerApp.TAG, MeasurementJsonConvertor.toJsonString(task.measurementDesc));
            schedule.add(task);
          } catch (IllegalArgumentException e) {
            Log.w(SpeedometerApp.TAG, "Could not create task from JSON: " + e);
            // Just skip it, and try the next one
          }
        }
      }
      
      this.lastCheckin = new Date();
      Log.i(SpeedometerApp.TAG, "Checkin complete, got " + schedule.size() +
          " new tasks");
      checkinSuccess = true;
      return schedule;
    } catch (JSONException e) {
      Log.e(SpeedometerApp.TAG, "Got exception during checkin: " + Log.getStackTraceString(e));
      throw new IOException("There is exception during checkin()");
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, "Got exception during checkin: " + Log.getStackTraceString(e));
      throw e;
    } finally {
      if (!checkinSuccess) {
        // Failure probably due to authToken expiration. Will authenticate upon next checkin.
        this.accountSelector.setAuthImmediately(true);
        this.authCookie = null;
      }
    }
  }
  
  public void uploadMeasurementResult(Vector<MeasurementResult> finishedTasks)
      throws IOException {    
    JSONArray resultArray = new JSONArray();
    for (MeasurementResult result : finishedTasks) {
      try {
        resultArray.put(MeasurementJsonConvertor.encodeToJson(result));
      } catch (JSONException e1) {
        Log.e(SpeedometerApp.TAG, "Error when adding " + result);
      }
    }
    
    Log.i(SpeedometerApp.TAG, "TaskSchedule.uploadMeasurementResult() uploading: " + 
        resultArray.toString());
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
    Log.i(SpeedometerApp.TAG, "TaskSchedule.uploadMeasurementResult() complete");
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
    
    /* TODO(Wenjie): the post method sometimes takes a very long time to finish.
     * POST_TIMEOUT_MILLISEC should be set in a more adaptive way based on the average
     * network condition under test. */
    HttpParams httpParameters = new BasicHttpParams();
    // Set the timeout in milliseconds until a connection is established.
    HttpConnectionParams.setConnectionTimeout(httpParameters, POST_TIMEOUT_MILLISEC);
    // Set the default socket timeout (SO_TIMEOUT)
    // in milliseconds which is the timeout for waiting for data.
    HttpConnectionParams.setSoTimeout(httpParameters, POST_TIMEOUT_MILLISEC);
    DefaultHttpClient client = new DefaultHttpClient(httpParameters);
    // TODO(mdw): For some reason this is not sending the cookie to the
    // test server, probably because the cookie itself is not properly
    // initialized. Below I manually set the Cookie header instead.
    CookieStore store = new BasicCookieStore();
    store.addCookie(authCookie);
    client.setCookieStore(store);
    Log.i(SpeedometerApp.TAG, "authCookie is: " + authCookie);
    
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
   * Initiates the process to get the authentication cookie for the user account.
   * Returns immediately.
   */
  public synchronized void getCookie() {
    if (isTestingServer()) {
      Log.i(SpeedometerApp.TAG, "Setting fakeAuthCookie");
      authCookie = getFakeAuthCookie();
      return;
    }
    if (this.accountSelector == null) {
      accountSelector = new AccountSelector(context, this);
    }
    
    try {
      // Authenticates if there are no ongoing ones
      if (accountSelector.getCheckinFuture() == null) {
        accountSelector.authenticate();
      }
    } catch (OperationCanceledException e) {
      Log.e(SpeedometerApp.TAG, "Unable to get auth cookie", e);
    } catch (AuthenticatorException e) {
      Log.e(SpeedometerApp.TAG, "Unable to get auth cookie", e);
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, "Unable to get auth cookie", e);
    }
  }
  
  /**
   * Resets the checkin variables in AccountSelector
   * */
  public void initializeAccountSelector() {
    accountSelector.resetCheckinFuture();
    accountSelector.setAuthImmediately(false);
  }
  
  private synchronized boolean checkGetCookie() {
    if (isTestingServer()) {
      authCookie = getFakeAuthCookie();
      return true;
    }
    Future<Cookie> getCookieFuture = accountSelector.getCheckinFuture();
    if (getCookieFuture == null) {
      Log.i(SpeedometerApp.TAG, "checkGetCookie called too early");
      return false;
    }
    if (getCookieFuture.isDone()) {
      try {
        authCookie = getCookieFuture.get();
        Log.i(SpeedometerApp.TAG, "Got authCookie: " + authCookie);
        return true;
      } catch (InterruptedException e) {
        Log.e(SpeedometerApp.TAG, "Unable to get auth cookie", e);
        return false;
      } catch (ExecutionException e) {
        Log.e(SpeedometerApp.TAG, "Unable to get auth cookie", e);
        return false;
      }
    } else {
      Log.i(SpeedometerApp.TAG, "getCookieFuture is not yet finished");
      return false;
    }
  }
  
  private void sendStringMsg(String str) {
    UpdateIntent intent = new UpdateIntent(str, UpdateIntent.MSG_ACTION);
    context.sendBroadcast(intent);    
  }
}

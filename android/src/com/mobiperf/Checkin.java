/*
 * Copyright 2012 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.mobiperf;

import com.mobiperf.measurements.RRCTask;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;

import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Handles checkins with the SpeedometerApp server.
 */
public class Checkin {
  private static final int POST_TIMEOUT_MILLISEC = 20 * 1000;
  private Context context;
  private Date lastCheckin;
  private volatile Cookie authCookie = null;
  private AccountSelector accountSelector = null;
  PhoneUtils phoneUtils;

  public Checkin(Context context) {
    phoneUtils = PhoneUtils.getPhoneUtils();
    this.context = context;
  }

  /** Shuts down the checkin thread */
  public void shutDown() {
    if (this.accountSelector != null) {
      this.accountSelector.shutDown();
    }
  }

  /** Return a fake authentication cookie for a test server instance */
  private Cookie getFakeAuthCookie() {
    BasicClientCookie cookie =
        new BasicClientCookie("dev_appserver_login",
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

  public List<MeasurementTask> checkin(ResourceCapManager resourceCapManager) throws IOException {
    Logger.i("Checkin.checkin() called");
    boolean checkinSuccess = false;
    try {
      JSONObject status = new JSONObject();
      DeviceInfo info = phoneUtils.getDeviceInfo();
      // TODO(Wenjie): There is duplicated info here, such as device ID.
      status.put("id", info.deviceId);
      status.put("manufacturer", info.manufacturer);
      status.put("model", info.model);
      status.put("os", info.os);
      status
          .put("properties", MeasurementJsonConvertor.encodeToJson(phoneUtils
              .getDeviceProperty()));
      resourceCapManager.updateDataUsage(ResourceCapManager.PHONEUTILCOST);

      Logger.d(status.toString());
      sendStringMsg("Checking in");

      String result = serviceRequest("checkin", status.toString());
      Logger.d("Checkin result: " + result);
      resourceCapManager.updateDataUsage(result.length());

      // Parse the result
      Vector<MeasurementTask> schedule = new Vector<MeasurementTask>();
      JSONArray jsonArray = new JSONArray(result);
      sendStringMsg("Checkin got " + jsonArray.length() + " tasks.");

      for (int i = 0; i < jsonArray.length(); i++) {
        Logger.d("Parsing index " + i);
        JSONObject json = jsonArray.optJSONObject(i);
        Logger.d("Value is " + json);
        // checkin task must support
        if (json != null
            && MeasurementTask.getMeasurementTypes().contains(json.get("type"))) {
          try {
            MeasurementTask task =
                MeasurementJsonConvertor.makeMeasurementTaskFromJson(json,
                    this.context);
            Logger.i(MeasurementJsonConvertor
                .toJsonString(task.measurementDesc));
            schedule.add(task);
          } catch (IllegalArgumentException e) {
            Logger.w("Could not create task from JSON: " + e);
            // Just skip it, and try the next one
          }
        }
      }

      this.lastCheckin = new Date();
      Logger.i("Checkin complete, got " + schedule.size() + " new tasks");
      checkinSuccess = true;
      return schedule;
    } catch (JSONException e) {
      Logger.e("Got exception during checkin", e);
      throw new IOException("There is exception during checkin()");
    } catch (IOException e) {
      Logger.e("Got exception during checkin", e);
      throw e;
    } finally {
      if (!checkinSuccess) {
        // Failure probably due to authToken expiration. Will authenticate upon next checkin.
        this.accountSelector.setAuthImmediately(true);
        this.authCookie = null;
      }
    }
  }

  /**
   * Upload results from non-RRC measurements to the server
   * 
   * @param resultArray a JSON array of results to date
   * @param resourceCapManager used to update data consumption based on traffic from the checkin
   * @throws IOException
   */
  public void uploadMeasurementResult(JSONArray resultArray, ResourceCapManager resourceCapManager)
      throws IOException {

    sendStringMsg("Uploading " + resultArray.length() + " measurement results.");
    Logger.i("TaskSchedule.uploadMeasurementResult() uploading: "
        + resultArray.toString());
    resourceCapManager.updateDataUsage(resultArray.toString().length());
    String response = serviceRequest("postmeasurement", resultArray.toString());
    try {
      JSONObject responseJson = new JSONObject(response);
      if (!responseJson.getBoolean("success")) {
        throw new IOException("Failure posting measurement result");
      }
    } catch (JSONException e) {
      throw new IOException(e.getMessage());
    }
    Logger.i("TaskSchedule.uploadMeasurementResult() complete");
    sendStringMsg("Result upload complete.");
  }

  /**
   * Send the RRC data to the server.  
   * 
   * Sent as a separate call because the data is formatted in a different, 
   * more complicated way than other measurement tasks.
   * TODO do this on checkin
   * 
   * @param data Contains data to upload
   * @throws IOException
   */
  public void uploadRrcInferenceData(RRCTask.RRCTestData data) throws IOException {
    DeviceInfo info = phoneUtils.getDeviceInfo();
    String network_id = phoneUtils.getNetwork();
    String[] parameters = data.toJSON(network_id, info.deviceId);
    try {
      for (String parameter : parameters) {
        Logger.w("Uploading RRC raw data: " + parameter);
        String response = serviceRequest("rrc/uploadRRCInference", parameter);
        Logger.w("Response from GAE: " + response);

        Logger.i("TaskSchedule.uploadMeasurementResult() complete");
        sendStringMsg("Result upload complete.");
      }
    } catch (IOException e) {
      throw new IOException(e.getMessage());
    } catch (NumberFormatException e) {
      e.printStackTrace();
    }
  }

  /**
   * Impact of packet sizes on rrc inference results.
   * TODO do this on checkin
   * 
   * @param sizeData Contains data to upload
   */
  public void uploadRrcInferenceSizeData(RRCTask.RRCTestData sizeData) {
    DeviceInfo info = phoneUtils.getDeviceInfo();
    String network_id = phoneUtils.getNetwork();
    String[] sizeParameters =
        sizeData.sizeDataToJSON(network_id, info.deviceId);

    try {
      for (String parameter : sizeParameters) {
        Logger.w("Uploading RRC size data: " + parameter);
        String response =
            serviceRequest("rrc/uploadRRCInferenceSizes", parameter);
        Logger.w("Response from GAE: " + response);
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  /**
   * Used to generate SSL sockets.
   */
  class MySSLSocketFactory extends SSLSocketFactory {
    SSLContext sslContext = SSLContext.getInstance("TLS");

    public MySSLSocketFactory(KeyStore truststore)
        throws NoSuchAlgorithmException, KeyManagementException,
        KeyStoreException, UnrecoverableKeyException {
      super(truststore);

      X509TrustManager tm = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
          // Do nothing
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
          // Do nothing
        }
      };

      sslContext.init(null, new TrustManager[] {tm}, null);
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port,
        boolean autoClose) throws IOException, UnknownHostException {
      return sslContext.getSocketFactory().createSocket(socket, host, port,
          autoClose);
    }

    @Override
    public Socket createSocket() throws IOException {
      return sslContext.getSocketFactory().createSocket();
    }
  }

  /**
   * Return an appropriately-configured HTTP client.
   */
  private HttpClient getNewHttpClient() {
    DefaultHttpClient client;
    try {
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);

      SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
      sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

      HttpParams params = new BasicHttpParams();
      HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
      HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

      HttpConnectionParams.setConnectionTimeout(params, POST_TIMEOUT_MILLISEC);
      HttpConnectionParams.setSoTimeout(params, POST_TIMEOUT_MILLISEC);

      SchemeRegistry registry = new SchemeRegistry();
      registry.register(new Scheme("http", PlainSocketFactory
          .getSocketFactory(), 80));
      registry.register(new Scheme("https", sf, 443));

      ClientConnectionManager ccm =
          new ThreadSafeClientConnManager(params, registry);
      client = new DefaultHttpClient(ccm, params);
    } catch (Exception e) {
      Logger.w("Unable to create SSL HTTP client", e);
      client = new DefaultHttpClient();
    }

    // TODO(mdw): For some reason this is not sending the cookie to the
    // test server, probably because the cookie itself is not properly
    // initialized. Below I manually set the Cookie header instead.
    CookieStore store = new BasicCookieStore();
    store.addCookie(authCookie);
    client.setCookieStore(store);
    return client;
  }

  private String serviceRequest(String url, String jsonString)
      throws IOException {

    if (this.accountSelector == null) {
      accountSelector = new AccountSelector(context);
    }

    if (!accountSelector.isAnonymous()) {
      synchronized (this) {
        if (authCookie == null) {
          if (!checkGetCookie()) {
            throw new IOException("No authCookie yet");
          }
        }
      }
    }

    HttpClient client = getNewHttpClient();
    String fullurl =
        (accountSelector.isAnonymous()
            ? phoneUtils.getAnonymousServerUrl()
            : phoneUtils.getServerUrl()) + "/" + url;
    Logger.i("Checking in to " + fullurl);
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
    if (!accountSelector.isAnonymous()) {
      // TODO(mdw): This should not be needed
      postMethod.setHeader("Cookie",
          authCookie.getName() + "=" + authCookie.getValue());
    }

    ResponseHandler<String> responseHandler = new BasicResponseHandler();
    Logger.i("Sending request: " + fullurl);
    String result = client.execute(postMethod, responseHandler);
    return result;
  }

  /**
   * Initiates the process to get the authentication cookie for the user account. Returns
   * immediately.
   */
  public synchronized void getCookie() {
    if (phoneUtils.isTestingServer(phoneUtils.getServerUrl())) {
      Logger.i("Setting fakeAuthCookie");
      authCookie = getFakeAuthCookie();
      return;
    }
    if (this.accountSelector == null) {
      accountSelector = new AccountSelector(context);
    }

    try {
      // Authenticates if there are no ongoing ones
      if (accountSelector.getCheckinFuture() == null) {
        accountSelector.authenticate();
      }
    } catch (OperationCanceledException e) {
      Logger.e("Unable to get auth cookie", e);
    } catch (AuthenticatorException e) {
      Logger.e("Unable to get auth cookie", e);
    } catch (IOException e) {
      Logger.e("Unable to get auth cookie", e);
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
    if (phoneUtils.isTestingServer(phoneUtils.getServerUrl())) {
      authCookie = getFakeAuthCookie();
      return true;
    }
    Future<Cookie> getCookieFuture = accountSelector.getCheckinFuture();
    if (getCookieFuture == null) {
      Logger.i("checkGetCookie called too early");
      return false;
    }
    if (getCookieFuture.isDone()) {
      try {
        authCookie = getCookieFuture.get();
        Logger.i("Got authCookie: " + authCookie);
        return true;
      } catch (InterruptedException e) {
        Logger.e("Unable to get auth cookie", e);
        return false;
      } catch (ExecutionException e) {
        Logger.e("Unable to get auth cookie", e);
        return false;
      }
    } else {
      Logger.i("getCookieFuture is not yet finished");
      return false;
    }
  }

  private void sendStringMsg(String str) {
    UpdateIntent intent = new UpdateIntent(str, UpdateIntent.MSG_ACTION);
    context.sendBroadcast(intent);
  }
}

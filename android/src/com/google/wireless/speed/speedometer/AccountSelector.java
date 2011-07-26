// Copyright 2011 Google Inc. All Rights Reserved.
package com.google.wireless.speed.speedometer;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Helper class for google account checkins
 * 
 * @author mdw@google.com (Matt Welsh)
 * @author wenjiezeng@google.com (Steve Zeng)
 *
 */
public class AccountSelector {
  private static final String ACCOUNT_TYPE = "com.google";
  private static final String ACCOUNT_NAME = "@google.com";
  private Context context;
  private Checkin checkin;
  private String authToken = null;
  private ExecutorService checkinExecutor = null;
  private Future<Cookie> checkinFuture = null;
  
  public AccountSelector(Context context, Checkin checkin) {
    this.context = context;
    this.checkin = checkin;
    this.checkinExecutor = Executors.newFixedThreadPool(1);
  }
  
  /** Returns the Future to monitor the checkin progress */
  public synchronized Future<Cookie> getCheckinFuture() {
    return this.checkinFuture;
  }
  
  /** After checkin finishes, the client of AccountSelector SHOULD reset checkinFuture */
  public synchronized void resetCheckinFuture() {
    this.checkinFuture = null;
  }
  
  /** Shuts down the executor thread */
  public void shutDown() {
    this.checkinExecutor.shutdown();
    this.checkinExecutor.shutdownNow();
  }
  
  /** Starts an authentication request  */
  public void authenticate() 
    throws OperationCanceledException, AuthenticatorException, IOException {
    Log.i(SpeedometerApp.TAG, "AccountSelector.authorize() running");
    
    AccountManager accountManager = AccountManager.get(
        context.getApplicationContext());
    if (this.authToken != null) {
      // There will be no effect on the token if it is still valid
      accountManager.invalidateAuthToken(ACCOUNT_TYPE, this.authToken);
    }
    
    Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
    Log.i(SpeedometerApp.TAG, "Got " + accounts.length + " accounts");
    
    if (accounts != null && accounts.length > 0) {
      // TODO(mdw): If multiple accounts, need to pick the correct one
      Account accountToUse = accounts[0];
      // We prefer google's corporate account to personal accounts such as somebody@gmail.com
      for (Account account : accounts) {
        if (account.name.toLowerCase().trim().endsWith(ACCOUNT_NAME)) {
          Log.i(SpeedometerApp.TAG, 
              "Using the preferred google.com account: " + account.name);
          accountToUse = account;
          break;
        }
      }
      
      Log.i(SpeedometerApp.TAG, "Trying to get auth token for " + accountToUse);
      
      AccountManagerFuture<Bundle> future = accountManager.getAuthToken(
          accountToUse, "ah", false, new AccountManagerCallback<Bundle>() {
        @Override
        public void run(AccountManagerFuture<Bundle> result) {
          Log.i(SpeedometerApp.TAG, "AccountManagerCallback invoked");
          getAuthToken(result);
        }},
        null);
      Log.i(SpeedometerApp.TAG, "AccountManager.getAuthToken returned " + future);
    } else {
      throw new RuntimeException("No google account found");
    }
  }
  
  private void getAuthToken(AccountManagerFuture<Bundle> result) {
    Log.i(SpeedometerApp.TAG, "getAuthToken() called, result " + result);
    String errMsg = "Failed to get login cookie. ";
    Bundle bundle;
    try {
      bundle = result.getResult();
      Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
      if (intent != null) {
        // User input required. (A UI will pop up for user's consent to allow
        // this app access account information.)
        Log.i(SpeedometerApp.TAG, "Starting account manager activity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
      } else {
        Log.i(SpeedometerApp.TAG, "Executing getCookie task");
        synchronized (this) {
          this.authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
          this.checkinFuture = checkinExecutor.submit(new GetCookieTask());
        }
      }
    } catch (OperationCanceledException e) {
      Log.e(SpeedometerApp.TAG, errMsg, e);
      throw new RuntimeException("Can't get login cookie", e);
    } catch (AuthenticatorException e) {
      Log.e(SpeedometerApp.TAG, errMsg, e);
      throw new RuntimeException("Can't get login cookie", e);
    } catch (IOException e) {
      Log.e(SpeedometerApp.TAG, errMsg, e);
      throw new RuntimeException("Can't get login cookie", e);
    }
  }
  
  private class GetCookieTask implements Callable<Cookie> {    
    @Override
    public Cookie call() {
      Log.i(SpeedometerApp.TAG, "GetCookieTask running: " + authToken);
      DefaultHttpClient httpClient = new DefaultHttpClient();
      try {
        String loginUrlPrefix = checkin.getServerUrl() +
          "/_ah/login?continue=" + checkin.getServerUrl() + 
          "&action=Login&auth=";
        // Don't follow redirects
        httpClient.getParams().setBooleanParameter(
            ClientPNames.HANDLE_REDIRECTS, false);
        HttpGet httpGet = new HttpGet(loginUrlPrefix + authToken);
        HttpResponse response;
        Log.i(SpeedometerApp.TAG, "Accessing: " + loginUrlPrefix + authToken);
        response = httpClient.execute(httpGet);
        if (response.getStatusLine().getStatusCode() != 302) {
          // Response should be a redirect to the "continue" URL.
          Log.e(SpeedometerApp.TAG, "Failed to get login cookie: " +
              loginUrlPrefix + " returned unexpected error code " +
              response.getStatusLine().getStatusCode());
          throw new RuntimeException("Failed to get login cookie: " +
              loginUrlPrefix + " returned unexpected error code " +
              response.getStatusLine().getStatusCode());
        }
        
        Log.i(SpeedometerApp.TAG, "Got " + 
            httpClient.getCookieStore().getCookies().size() + " cookies back");
        
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
          Log.i(SpeedometerApp.TAG, "Checking cookie " + cookie);
          if (cookie.getName().equals("SACSID")
              || cookie.getName().equals("ACSID")) {
            Log.i(SpeedometerApp.TAG, "Got cookie " + cookie);
            return cookie;
          }
        }
        Log.e(SpeedometerApp.TAG, "No (S)ASCID cookies returned");
        throw new RuntimeException("Failed to get login cookie: " +
            loginUrlPrefix + " did not return any (S)ACSID cookie");
      } catch (ClientProtocolException e) {
        Log.e(SpeedometerApp.TAG, "Failed to get login cookie", e);
        throw new RuntimeException("Failed to get login cookie", e);
      } catch (IOException e) {
        Log.e(SpeedometerApp.TAG, "Failed to get login cookie", e);
        throw new RuntimeException("Failed to get login cookie", e);
      } finally {
        httpClient.getParams().setBooleanParameter(
            ClientPNames.HANDLE_REDIRECTS, true);
      }
    }
  }
    

}

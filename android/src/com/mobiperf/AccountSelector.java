/* Copyright 2012 Google Inc.
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
package com.mobiperf;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

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

import com.mobiperf.util.PhoneUtils;
import com.mobiperf.R;

/**
 * Helper class for google account checkins
 */
public class AccountSelector {
  private static final String ACCOUNT_TYPE = "com.google";
  private static final String ACCOUNT_NAME = "@google.com";
  // The authentication period in milliseconds
  private static final long AUTHENTICATE_PERIOD_MSEC = 24 * 3600 * 1000;
  private Context context;
  private String authToken = null;
  private ExecutorService checkinExecutor = null;
  private Future<Cookie> checkinFuture = null;
  private long lastAuthTime = 0;
  private boolean authImmediately = false;
  private PhoneUtils phoneUtils;

  private boolean isAnonymous = true;
  public boolean isAnonymous() { return isAnonymous; }
  
  public AccountSelector(Context context) {
    this.context = context;
    this.checkinExecutor = Executors.newFixedThreadPool(1);
    this.phoneUtils = PhoneUtils.getPhoneUtils();
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
    // shutdown() removes all previously submitted task and no new tasks are accepted 
    this.checkinExecutor.shutdown();
    // shutdownNow stops all currently executing tasks
    this.checkinExecutor.shutdownNow();
  }
  
  /** Allows clients of AccountSelector to request an authentication upon the next call
   * to authenticate() */
  public synchronized void setAuthImmediately(boolean val) {
    this.authImmediately = val;
  }
  
  private synchronized boolean shouldAuthImmediately() {
    return this.authImmediately;
  }
  
  private synchronized void setLastAuthTime(long lastTime) {
    this.lastAuthTime = lastTime;
  }
  
  private synchronized long getLastAuthTime() {
    return this.lastAuthTime;
  }
  
  /**
   * Return the list of account names for users to select
   */
  public static String[] getAccountList(Context context) {
    AccountManager accountManager = AccountManager.get(context.getApplicationContext());
    Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
    int numAccounts = accounts == null ? 1 : accounts.length + 1;
    String[] accountNames = new String[numAccounts];
    for (int i = 0 ; i < accounts.length ; i++) {
      accountNames[i] = accounts[i].name;
    }
    accountNames[numAccounts - 1] = context.getString(R.string.defaultUser);
    return accountNames;
  }
  
  /** Starts an authentication request  */
  public void authenticate() 
    throws OperationCanceledException, AuthenticatorException, IOException {
    Logger.i("AccountSelector.authenticate() running");
    /* We only need to authenticate every AUTHENTICATE_PERIOD_MILLI milliseconds, during
     * which we can reuse the cookie. If authentication fails due to expired
     * authToken, the client of AccountSelector can call authImmedately() to request
     * authenticate() upon the next checkin
     */
    long authTimeLast = this.getLastAuthTime();
    long timeSinceLastAuth = System.currentTimeMillis() - authTimeLast;
    if (!this.shouldAuthImmediately() && authTimeLast != 0 &&
        (timeSinceLastAuth < AUTHENTICATE_PERIOD_MSEC)) {
      return;
    }
    
    Logger.i("Authenticating. Last authentication is " + 
        timeSinceLastAuth / 1000 / 60 + " minutes ago. ");
    
    AccountManager accountManager = AccountManager.get(context.getApplicationContext());
    if (this.authToken != null) {
      // There will be no effect on the token if it is still valid
      Logger.i("Invalidating token");
      accountManager.invalidateAuthToken(ACCOUNT_TYPE, this.authToken);
    }
    
    Account[] accounts = accountManager.getAccountsByType(ACCOUNT_TYPE);
    Logger.i("Got " + accounts.length + " accounts");
    
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
    String selectedAccount = prefs.getString(Config.PREF_KEY_SELECTED_ACCOUNT, null);
   
    final String defaultUserName = context.getString(R.string.defaultUser);
    isAnonymous = true;
    if (selectedAccount != null && selectedAccount.equals(defaultUserName)) {
      return;
    }

    if (accounts != null && accounts.length > 0) {
      // Default account should be the Anonymous account
      Account accountToUse = accounts[accounts.length-1];
      if (!accounts[accounts.length-1].name.equals(defaultUserName)) {
        for (Account account : accounts) {
          if (account.name.equals(defaultUserName)) {
            accountToUse = account;
            break;
          }
        }
      }
      if (selectedAccount != null) {
        for (Account account : accounts) {
          if (account.name.equals(selectedAccount)) {
            accountToUse = account;
            break;
          }
        }
      }
      
      isAnonymous = accountToUse.name.equals(defaultUserName);
     
      if (isAnonymous) {
        Logger.d("Skipping authentication as account is " + defaultUserName);
        return;
      }

      Logger.i("Trying to get auth token for " + accountToUse);
      AccountManagerFuture<Bundle> future = accountManager.getAuthToken(
          accountToUse, "ah", false, new AccountManagerCallback<Bundle>() {
        @Override
        public void run(AccountManagerFuture<Bundle> result) {
          Logger.i("AccountManagerCallback invoked");
          try {
            getAuthToken(result);
          } catch (RuntimeException e) {
            Logger.e("Failed to get authToken", e);
            /* TODO(Wenjie): May ask the user whether to quit the app nicely here if a number
             * of trials have been made and failed. Since Speedometer is basically useless 
             * without checkin
             */
          }
        }},
        null);
      Logger.i("AccountManager.getAuthToken returned " + future);
    } else {
      throw new RuntimeException("No google account found");
    }
  }
  
  private void getAuthToken(AccountManagerFuture<Bundle> result) {
    Logger.i("getAuthToken() called, result " + result);
    String errMsg = "Failed to get login cookie. ";
    Bundle bundle;
    try {
      bundle = result.getResult();
      Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
      if (intent != null) {
        // User input required. (A UI will pop up for user's consent to allow
        // this app access account information.)
        Logger.i("Starting account manager activity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
      } else {
        Logger.i("Executing getCookie task");
        synchronized (this) {
          this.authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
          this.checkinFuture = checkinExecutor.submit(new GetCookieTask());
        }
      }
    } catch (OperationCanceledException e) {
      Logger.e(errMsg, e);
      throw new RuntimeException("Can't get login cookie", e);
    } catch (AuthenticatorException e) {
      Logger.e(errMsg, e);
      throw new RuntimeException("Can't get login cookie", e);
    } catch (IOException e) {
      Logger.e(errMsg, e);
      throw new RuntimeException("Can't get login cookie", e);
    }
  }
  
  private class GetCookieTask implements Callable<Cookie> {    
    @Override
    public Cookie call() {
      Logger.i("GetCookieTask running: " + authToken);
      DefaultHttpClient httpClient = new DefaultHttpClient();
      boolean success = false;
      try {
        String loginUrlPrefix = phoneUtils.getServerUrl() +
          "/_ah/login?continue=" + phoneUtils.getServerUrl() + 
          "&action=Login&auth=";
        // Don't follow redirects
        httpClient.getParams().setBooleanParameter(
            ClientPNames.HANDLE_REDIRECTS, false);
        HttpGet httpGet = new HttpGet(loginUrlPrefix + authToken);
        HttpResponse response;
        Logger.i("Accessing: " + loginUrlPrefix + authToken);
        response = httpClient.execute(httpGet);
        if (response.getStatusLine().getStatusCode() != 302) {
          // Response should be a redirect to the "continue" URL.
          Logger.e("Failed to get login cookie: " +
              loginUrlPrefix + " returned unexpected error code " +
              response.getStatusLine().getStatusCode());
          throw new RuntimeException("Failed to get login cookie: " +
              loginUrlPrefix + " returned unexpected error code " +
              response.getStatusLine().getStatusCode());
        }
        
        Logger.i("Got " + 
            httpClient.getCookieStore().getCookies().size() + " cookies back");
        
        for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
          Logger.i("Checking cookie " + cookie);
          if (cookie.getName().equals("SACSID")
              || cookie.getName().equals("ACSID")) {
            Logger.i("Got cookie " + cookie);
            setLastAuthTime(System.currentTimeMillis());
            success = true;
            return cookie;
          }
        }
        Logger.e("No (S)ASCID cookies returned");
        throw new RuntimeException("Failed to get login cookie: " +
            loginUrlPrefix + " did not return any (S)ACSID cookie");
      } catch (ClientProtocolException e) {
        Logger.e("Failed to get login cookie", e);
        throw new RuntimeException("Failed to get login cookie", e);
      } catch (IOException e) {
        Logger.e("Failed to get login cookie", e);
        throw new RuntimeException("Failed to get login cookie", e);
      } finally {
        httpClient.getParams().setBooleanParameter(
            ClientPNames.HANDLE_REDIRECTS, true);
        if (!success) {
          resetCheckinFuture();
        }
      }
    }
  }
    

}

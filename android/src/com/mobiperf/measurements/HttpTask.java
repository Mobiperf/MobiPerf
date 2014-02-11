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

package com.mobiperf.measurements;

import com.mobiperf.Config;
import com.mobiperf.Logger;
import com.mobiperf.MeasurementDesc;
import com.mobiperf.MeasurementError;
import com.mobiperf.MeasurementResult;
import com.mobiperf.MeasurementTask;
import com.mobiperf.util.MeasurementJsonConvertor;
import com.mobiperf.util.PhoneUtils;
import com.mobiperf.util.Util;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.util.Base64;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.Map;

/**
 * A Callable class that performs download throughput test using HTTP get
 */
public class HttpTask extends MeasurementTask {
  // Type name for internal use
  public static final String TYPE = "http";
  // Human readable name for the task
  public static final String DESCRIPTOR = "HTTP";
  /* TODO(Wenjie): Depending on state machine configuration of cell tower's radio,
   * the size to find the 'real' bandwidth of the phone may be network dependent.  
   */
  // The maximum number of bytes we will read from requested URL. Set to 1Mb.
  public static final long MAX_HTTP_RESPONSE_SIZE = 1024 * 1024;
  // The size of the response body we will report to the service.
  // If the response is larger than MAX_BODY_SIZE_TO_UPLOAD bytes, we will 
  // only report the first MAX_BODY_SIZE_TO_UPLOAD bytes of the body.
  public static final int MAX_BODY_SIZE_TO_UPLOAD = 1024;
  // The buffer size we use to read from the HTTP response stream
  public static final int READ_BUFFER_SIZE = 1024;
  // Not used by the HTTP protocol. Just in case we do not receive a status line from the response
  public static final int DEFAULT_STATUS_CODE = 0;
  
  private AndroidHttpClient httpClient = null;
  
  // Track data consumption for this task to avoid exceeding user's limit  
  private long dataConsumed;

  public HttpTask(MeasurementDesc desc, Context parent) {
    super(new HttpDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
      desc.count, desc.priority, desc.parameters), parent);
    dataConsumed = 0;
  }
  
  /**
   * The description of a HTTP measurement 
   */
  public static class HttpDesc extends MeasurementDesc {
    public String url;
    private String method;
    private String headers;
    private String body;

    public HttpDesc(String key, Date startTime, Date endTime,
                      double intervalSec, long count, long priority, Map<String, String> params) 
                      throws InvalidParameterException {
      super(HttpTask.TYPE, key, startTime, endTime, intervalSec, count, priority, params);
      initializeParams(params);
      if (this.url == null || this.url.length() == 0) {
        throw new InvalidParameterException("URL for http task is null");
      }
    }
    
    @Override
    protected void initializeParams(Map<String, String> params) {
      
      if (params == null) {
        return;
      }
      
      this.url = params.get("url");
      if (!this.url.startsWith("http://") && !this.url.startsWith("https://")) {
        this.url = "http://" + this.url;
      }
      
      this.method = params.get("method");
      if (this.method == null || this.method.isEmpty()) {
        this.method = "get";
      }
      this.headers = params.get("headers");      
      this.body = params.get("body");
    }
    
    @Override
    public String getType() {
      return HttpTask.TYPE;
    }
    
  }
  
  /**
   * Returns a copy of the HttpTask
   */
  @Override
  public MeasurementTask clone() {
    MeasurementDesc desc = this.measurementDesc;
    HttpDesc newDesc = new HttpDesc(desc.key, desc.startTime, desc.endTime, 
        desc.intervalSec, desc.count, desc.priority, desc.parameters);
    return new HttpTask(newDesc, parent);
  }
  
  /** Runs the HTTP measurement task. Will acquire power lock to ensure wifi is not turned off */
  @Override
  public MeasurementResult call() throws MeasurementError {
    
    int statusCode = HttpTask.DEFAULT_STATUS_CODE;
    long duration = 0;
    long originalHeadersLen = 0;
    long originalBodyLen;
    String headers = null;
    ByteBuffer body = ByteBuffer.allocate(HttpTask.MAX_BODY_SIZE_TO_UPLOAD);
    boolean success = false;
    String errorMsg = "";
    InputStream inputStream = null;
    
    // This is not the ideal way of doing things, as we can pick up data
    // from other processes and be overly cautious in running measurements.
    // However, taking the packet sizes is completely inaccurate, and it's hard to
    // come up with a consistent expected value, unlike with DNS
    RRCTask.PacketMonitor packetmonitor = new RRCTask.PacketMonitor();
    packetmonitor.setBySize();
    packetmonitor.readCurrentPacketValues();
    
    try {
      // set the download URL, a URL that points to a file on the Internet
      // this is the file to be downloaded
      HttpDesc task = (HttpDesc) this.measurementDesc;
      String urlStr = task.url;
          
      // TODO(Wenjie): Need to set timeout for the HTTP methods
      httpClient = AndroidHttpClient.newInstance(Util.prepareUserAgent(this.parent));
      HttpRequestBase request = null;
      if (task.method.compareToIgnoreCase("head") == 0) {
        request = new HttpHead(urlStr);
      } else if (task.method.compareToIgnoreCase("get") == 0) {
        request = new HttpGet(urlStr);
      } else if (task.method.compareToIgnoreCase("post") == 0) {
        request = new HttpPost(urlStr);
        HttpPost postRequest = (HttpPost) request;
        postRequest.setEntity(new StringEntity(task.body));
      } else {
        // Use GET by default
        request = new HttpGet(urlStr);
      }      
      
      if (task.headers != null && task.headers.trim().length() > 0) {
        for (String headerLine : task.headers.split("\r\n")) {
          String tokens[] = headerLine.split(":");
          if (tokens.length == 2) {
            request.addHeader(tokens[0], tokens[1]);
          } else {
            throw new MeasurementError("Incorrect header line: " + headerLine);
          }
        }
      } 
      
      byte[] readBuffer = new byte[HttpTask.READ_BUFFER_SIZE];
      int readLen;      
      int totalBodyLen = 0;
      
      long startTime = System.currentTimeMillis();
      HttpResponse response = httpClient.execute(request);
      
      /* TODO(Wenjie): HttpClient does not automatically handle the following codes
       * 301 Moved Permanently. HttpStatus.SC_MOVED_PERMANENTLY
       * 302 Moved Temporarily. HttpStatus.SC_MOVED_TEMPORARILY
       * 303 See Other. HttpStatus.SC_SEE_OTHER
       * 307 Temporary Redirect. HttpStatus.SC_TEMPORARY_REDIRECT
       * 
       * We may want to fetch instead from the redirected page. 
       */
      StatusLine statusLine = response.getStatusLine();
      if (statusLine != null) {
        statusCode = statusLine.getStatusCode();
        success = (statusCode == 200);
      }
      
      /* For HttpClient to work properly, we still want to consume the entire response even if
       * the status code is not 200 
       */
      HttpEntity responseEntity = response.getEntity();      
      originalBodyLen = responseEntity.getContentLength();
      long expectedResponseLen = HttpTask.MAX_HTTP_RESPONSE_SIZE;
      // getContentLength() returns negative number if body length is unknown
      if (originalBodyLen > 0) {
        expectedResponseLen = originalBodyLen;
      }
      
      if (responseEntity != null) {
        inputStream = responseEntity.getContent();
        while ((readLen = inputStream.read(readBuffer)) > 0 
            && totalBodyLen <= HttpTask.MAX_HTTP_RESPONSE_SIZE) {
          totalBodyLen += readLen;
          // Fill in the body to report up to MAX_BODY_SIZE
          if (body.remaining() > 0) {
            int putLen = body.remaining() < readLen ? body.remaining() : readLen; 
            body.put(readBuffer, 0, putLen);
          }
          this.progress = (int) (100 * totalBodyLen / expectedResponseLen);
          this.progress = Math.min(Config.MAX_PROGRESS_BAR_VALUE, progress);
          broadcastProgressForUser(this.progress);
        }
        duration = System.currentTimeMillis() - startTime;
      }
                 
      Header[] responseHeaders = response.getAllHeaders();
      if (responseHeaders != null) {
        headers = "";
        for (Header hdr : responseHeaders) {
          /*
           * TODO(Wenjie): There can be preceding and trailing white spaces in
           * each header field. I cannot find internal methods that return the
           * number of bytes in a header. The solution here assumes the encoding
           * is one byte per character.
           */
          originalHeadersLen += hdr.toString().length();
          headers += hdr.toString() + "\r\n";
        }
      }
      
      PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
      
      MeasurementResult result = new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
          phoneUtils.getDeviceProperty(), HttpTask.TYPE, System.currentTimeMillis() * 1000,
          success, this.measurementDesc);
      
      result.addResult("code", statusCode);      
     
      dataConsumed = packetmonitor.getPacketsSentDiff();
      
      if (success) {
        result.addResult("time_ms", duration);
        result.addResult("headers_len", originalHeadersLen);
        result.addResult("body_len", totalBodyLen);
        result.addResult("headers", headers);
        result.addResult("body", Base64.encodeToString(body.array(), Base64.DEFAULT));
      }
      
      Logger.i(MeasurementJsonConvertor.toJsonString(result));
      return result;    
    } catch (MalformedURLException e) {
      errorMsg += e.getMessage() + "\n";
      Logger.e(e.getMessage());
    } catch (IOException e) {
      errorMsg += e.getMessage() + "\n";
      Logger.e(e.getMessage());
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          Logger.e("Fails to close the input stream from the HTTP response");
        }
      }
      if (httpClient != null) {
        httpClient.close();
      }

    }
    throw new MeasurementError("Cannot get result from HTTP measurement because " + 
      errorMsg);
  }  

  @SuppressWarnings("rawtypes")
  public static Class getDescClass() throws InvalidClassException {
    return HttpDesc.class;
  }
  
  @Override
  public String getType() {
    return HttpTask.TYPE;
  }
  
  @Override
  public String getDescriptor() {
    return DESCRIPTOR;
  }
  
  @Override
  public String toString() {
    HttpDesc desc = (HttpDesc) measurementDesc;
    return "[HTTP " + desc.method + "]\n  Target: " + desc.url + "\n  Interval (sec): " + 
        desc.intervalSec + "\n  Next run: " + desc.startTime;
  }
  
  @Override
  public void stop() {
    if (httpClient != null) {
      httpClient.close();
    }
  }

  /**
   * Data used so far by the task.
   * 
   * To calculate this, we measure <i>all</i> data sent while the task
   * is running. This will tend to overestimate the data consumed, but due
   * to retransmissions, etc, especially when signal strength is poor, attempting
   * to calculate the size directly will tend to greatly underestimate the data
   * consumed.
   */
  @Override
  public long getDataConsumed() {
    return dataConsumed;
  }
}

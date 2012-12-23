package com.mobiperf.util;

import com.mobiperf.speedometer.Logger;

import android.content.Context;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;

import org.json.JSONException;
import org.json.JSONObject;

public class MLabNS {

  /** Used by measurement tests if MLabNS should be used to retrieve the real server target. */
  static public final String TARGET = "m-lab";

  /**
   * Query MLab-NS to get an FQDN for the given tool.
   */
  static public String Lookup(Context context, String tool) {
    return Lookup(context, tool, "ipv4");
  }

  /**
   * Query MLab-NS to get an FQDN for the given tool and address family.
   */
  static public String Lookup(Context context, String tool, String address_family) {
    final int maxResponseSize = 1024;

    ByteBuffer body = ByteBuffer.allocate(maxResponseSize);
    InputStream inputStream = null;

    try {
      // TODO(dominic): Need to set timeout for the HTTP methods
      // TODO(dominic): This should not be done on the UI thread.
      DefaultHttpClient httpClient = new DefaultHttpClient();
      Logger.d("Creating request GET for mlab-ns");
      // TODO(dominich): Remove address_family and allow for IPv6.
      String url = "http://mlab-ns.appspot.com/" + tool +
          "?format=json&address_family=" + address_family;
      Logger.i("Sending request: " + url);
      HttpGet request = new HttpGet(url);
      request.setHeader("User-Agent", Util.prepareUserAgent(context));

      HttpResponse response = httpClient.execute(request);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new InvalidParameterException(
            "Received status " + response.getStatusLine().getStatusCode() + " from mlab-ns");
      }
      Logger.d("STATUS OK");

      String body_str = getResponseBody(response);
      Logger.i("Received from m-lab-ns: " + body_str);
      JSONObject json = new JSONObject(body_str);
      return String.valueOf(json.getString("fqdn"));
    } catch (IOException e) {
      Logger.e("IOException trying to contact m-lab-ns: " + e.getMessage());
      throw new InvalidParameterException(e.getMessage());
    } catch (JSONException e) {
      Logger.e("JSONException trying to contact m-lab-ns: " + e.getMessage());
      throw new InvalidParameterException(e.getMessage());
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {
          Logger.e("Failed to close the input stream from the HTTP response");
        }
      }
    }
  }

  static private String getContentCharSet(final HttpEntity entity) throws ParseException {
    if (entity == null) {
      throw new IllegalArgumentException("entity may not be null");
    }

    String charset = null;
    if (entity.getContentType() != null) {
      HeaderElement values[] = entity.getContentType().getElements();
      if (values.length > 0) {
        NameValuePair param = values[0].getParameterByName("charset");
        if (param != null) {
          charset = param.getValue();
        }
      }
    }
    return charset;
  }

  static private String getResponseBodyFromEntity(HttpEntity entity)
      throws IOException, ParseException {
    if (entity == null) {
      throw new IllegalArgumentException("entity may not be null");
    }

    InputStream instream = entity.getContent();
    if (instream == null) {
      return "";
    }

    if (entity.getContentEncoding() != null) {
      if ("gzip".equals(entity.getContentEncoding().getValue())) {
        instream = new GZIPInputStream(instream);
      }
    }

    if (entity.getContentLength() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("HTTP entity too large to be buffered into memory");
    }

    String charset = getContentCharSet(entity);
    if (charset == null) {
      charset = HTTP.DEFAULT_CONTENT_CHARSET;
    }

    Reader reader = new InputStreamReader(instream, charset);
    StringBuilder buffer = new StringBuilder();

    try {
      char[] tmp = new char[1024];
      int l;
      while ((l = reader.read(tmp, 0, tmp.length)) != -1) {
        Logger.d("  reading: " + tmp);
        buffer.append(tmp);
      }
    } finally {
      reader.close();
    }

    return buffer.toString();
  }

  static private String getResponseBody(HttpResponse response) throws IllegalArgumentException {
    String response_text = null;
    HttpEntity entity = null;

    if (response == null) {
      throw new IllegalArgumentException("response may not be null");
    }

    try {
      entity = response.getEntity();
      response_text = getResponseBodyFromEntity(entity);
    } catch (ParseException e) {
      e.printStackTrace();
    } catch (IOException e) {
      if (entity != null) {
        try {
          entity.consumeContent();
        } catch (IOException e1) {
        }
      }
    }
    return response_text;
  }
}

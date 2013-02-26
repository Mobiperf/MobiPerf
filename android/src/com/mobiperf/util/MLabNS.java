package com.mobiperf.util;

import com.mobiperf.Logger;

import android.content.Context;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.ArrayList;
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
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MLabNS {

  /** Used by measurement tests if MLabNS should be used to retrieve the real server target. */
  static public final String TARGET = "mlab";

  /**
   * Query MLab-NS to get an FQDN for the given tool.
   */
  static public ArrayList<String> Lookup(Context context, String tool) {
    return Lookup(context, tool, null, "fqdn");
  }

  /**
   * Query MLab-NS to get an FQDN/IP for the given tool and address family.
   * @param field: fqdn or ip
   */
  static public ArrayList<String> Lookup(Context context, String tool, 
                              String address_family, String field) {
    final int maxResponseSize = 1024;
    // Set the timeout in milliseconds until a connection is established.
    final int timeoutConnection = 5000;
    // Set the socket timeout in milliseconds.
    final int timeoutSocket = 5000;

    ByteBuffer body = ByteBuffer.allocate(maxResponseSize);
    InputStream inputStream = null;
    
    // Sanitize for possible returned field
    if ( field != "fqdn" && field != "ip" ) {
      return null;
    }
    
    try {
      HttpParams httpParameters = new BasicHttpParams();
      HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
      HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
      DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
      
      Logger.d("Creating request GET for mlab-ns");
      String url = "http://mlab-ns.appspot.com/" + tool + "?format=json";
      if (address_family == "ipv4" || address_family == "ipv6") {
        url += "&address_family=" + address_family;
      }
      HttpGet request = new HttpGet(url);
      request.setHeader("User-Agent", Util.prepareUserAgent(context));

      HttpResponse response = httpClient.execute(request);
      if (response.getStatusLine().getStatusCode() != 200) {
        throw new InvalidParameterException(
            "Received status " + response.getStatusLine().getStatusCode() + " from mlab-ns");
      }
      Logger.d("STATUS OK");

      String body_str = getResponseBody(response);
      JSONObject json = new JSONObject(body_str);
      Logger.d("Field Type is " + json.get(field).getClass().getName());
      ArrayList<String> mlabNSResult = new ArrayList<String>();
      if (json.get(field) instanceof JSONArray) {
        // Convert array value into ArrayList
        JSONArray jsonArray = (JSONArray)json.get(field);
        for (int i = 0; i < jsonArray.length(); i++) {
          mlabNSResult.add(jsonArray.get(i).toString());
        }
      } else if (json.get(field) instanceof String) {
        // Append the string into ArrayList
        mlabNSResult.add(String.valueOf(json.getString(field)));
      } else {
        throw new InvalidParameterException("Unknown type " + 
                                            json.get(field).getClass().toString() + 
                                            " of value " + json.get(field));
      }
      return mlabNSResult;
    } catch (SocketTimeoutException e) {
      Logger.e("SocketTimeoutException trying to contact m-lab-ns");
      // e.getMessage() is null       
      throw new InvalidParameterException("Connect to m-lab-ns timeout. Please try again.");
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

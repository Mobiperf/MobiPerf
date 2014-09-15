package servers;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;

public class UplinkWorker extends Thread {
  private Socket client = null;

  private ArrayList<Double> tps_result;
  public int size = 0;
  public long testStartTime = 0; //test start time, used to determine slow start period
  public long startTime = 0; //start time of this period to calculate throughput
  public final static long SAMPLE_PERIOD = 1000; 
  public final static long SLOW_START_PERIOD = 5000; //empirically set to 5 seconds 

  public UplinkWorker() {
    tps_result = new ArrayList<Double>();
    testStartTime = System.currentTimeMillis();
  }
  
  public void setSocket(Socket client) {
    this.client = client;
  }

  public void run() {
    InputStream iStream = null;
    OutputStream oStream = null;
    try {
      client.setSoTimeout(Definition.RECV_TIMEOUT);
      client.setTcpNoDelay(true);

      iStream = client.getInputStream();
      oStream = client.getOutputStream(); 
      SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyyMMdd:HH:mm:ss:SSS");
      long threadId = this.getId();
      String startDate = sDateFormat.format(new Date()).toString();
      System.out.println("[" + startDate + "]" + " Uplink worker <" +
                         threadId + "> Thread starts");
      
      int readLen;
      byte [] buffer = new byte[Definition.BUFFER_SIZE];
      String recvData;
      while (true) {
        readLen = iStream.read(buffer, 0, buffer.length);
        if (readLen > 0) {
          recvData = new String(buffer).substring(0, readLen);
          // str = "data*" last message at str.substring(5-"*".length(), 5)
          if (readLen >= Definition.UPLINK_FINISH_MSG.length() && 
            recvData.substring(readLen - Definition.UPLINK_FINISH_MSG.length(), 
                               readLen).equals(Definition.UPLINK_FINISH_MSG)) {
            System.out.println("LAST MSG detected break");
            break;
          }
          updateSize(readLen);
        }
        else break;
      }

      if (tps_result.size() > 0) {
        String result = "";
        for (int i = 0; i < tps_result.size() - 1; i++)
          result += tps_result.get(i) + "#";
        result += tps_result.get(tps_result.size() - 1);
        byte [] finalResult = result.getBytes();
        oStream.write(finalResult, 0, finalResult.length);
        oStream.flush();
      }      
      String endDate = sDateFormat.format(new Date()).toString();
      System.out.println("[" + endDate + "]" + " Uplink worker <" +
                         threadId + "> Thread ends");

    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Uplink worker failed: port <" +
                         Definition.PORT_DOWNLINK + ">");
    } finally {
      if (null != oStream) {
        try {
          oStream.close();
        } catch (IOException e) {
          // nothing to be done, really; logging is probably over kill
          System.err.println("Error closing socket output stream.");
        }
      }
      if (null != iStream) {
        try {
          iStream.close();
        } catch (IOException e) {
          // nothing to be done, really; logging is probably over kill
          System.err.println("Error closing socket input stream.");
        }
        try {
          client.close();
        } catch (IOException e) {
          // nothing to be done, really; logging is probably over kill
          System.err.println("Error closing socket client.");
        }
      }
    }
  }

  private void updateSize(int delta) {
    double gtime = System.currentTimeMillis() - testStartTime;
    if (gtime < SLOW_START_PERIOD) //ignore slow start
      return;
    if (startTime == 0) {
      startTime = System.currentTimeMillis();
      size = 0;
    }
    size += delta;
    double time = System.currentTimeMillis() - startTime;
    if (time < SAMPLE_PERIOD) {
      return;
    } else {
      //time is in milli, so already kbps
      double throughput = (double)size * 8.0 / time;
      System.out.println("_throughput: " + throughput + " kbps_Time(sec): "
                         + (gtime / 1000.0));
      tps_result.add(throughput);
      size = 0;
      startTime = System.currentTimeMillis();
    }  
  }
}

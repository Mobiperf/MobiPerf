package servers;

// import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Date;
import java.text.SimpleDateFormat;

public class DownlinkWorker extends Thread {
  private Socket client = null;

  public void setSocket(Socket client) {
    this.client = client;
  }

  public void run() {
    OutputStream oStream = null;
    try {
      client.setSoTimeout(Definition.RECV_TIMEOUT);
      oStream = client.getOutputStream();

      SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyyMMdd:HH:mm:ss:SSS");
      long threadId = this.getId();
      String startDate = sDateFormat.format(new Date()).toString();
      System.out.println("[" + startDate + "]" + " Downlink worker <" +
                         threadId + "> Thread starts");

      long start = System.currentTimeMillis();
      long end = System.currentTimeMillis();

      byte [] buffer = new byte[Definition.THROUGHPUT_DOWN_SEGMENT_SIZE];
      Utilities.genRandomByteArray(buffer);
      while(end - start < Definition.DURATION_IPERF_MILLISECONDS) {
        oStream.write(buffer, 0, buffer.length);
        oStream.flush();
        end = System.currentTimeMillis();
      }
      String endDate = sDateFormat.format(new Date()).toString();
      System.out.println("[" + endDate + "]" + " Downlink worker <" +
                         threadId + "> Thread ends");
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Downlink worker failed: port <" +
                         Definition.PORT_DOWNLINK + ">");
    } finally {
      if (null != oStream) {
        try {
          oStream.close();
        } catch (IOException e) {
          // nothing to be done, really; logging is probably over kill
          System.err.println("Error closing socket output stream.");
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
}

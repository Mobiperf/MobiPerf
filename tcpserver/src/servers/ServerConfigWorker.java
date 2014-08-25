package servers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class ServerConfigWorker extends Thread {
  private Socket client = null;

  public void setSocket(Socket client) {
    this.client = client;
  }
  
  public void run() {
    OutputStream oStream = null;
    try {
      client.setSoTimeout(Definition.RECV_TIMEOUT);
      client.setTcpNoDelay(true);
      oStream = client.getOutputStream(); 

      // TODO (Haokun): Use JSON for multiple configuration data if necessary 
      byte [] finalResult = Definition.SERVER_VERSION.getBytes();
      oStream.write(finalResult, 0, finalResult.length);
      oStream.flush();
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Configuration worker failed: port <" +
                         Definition.PORT_CONFIG + ">");
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

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
    try {
      client.setSoTimeout(Definition.RECV_TIMEOUT);
      client.setTcpNoDelay(true);
      OutputStream oStream = client.getOutputStream(); 

      // TODO (Haokun): Use JSON for multiple configuration data if necessary 
      byte [] finalResult = Definition.SERVER_VERSION.getBytes();
      oStream.write(finalResult, 0, finalResult.length);
      oStream.flush();
      
      oStream.close();
      client.close();
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Configuration worker failed: port <" +
                         Definition.PORT_CONFIG + ">");
    }
  }
}

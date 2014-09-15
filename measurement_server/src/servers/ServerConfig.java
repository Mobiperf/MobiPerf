package servers;

import java.io.IOException;
import java.net.ServerSocket;

public class ServerConfig {
  public static void main(String[] argv){
    int port = Definition.PORT_CONFIG;
    ServerSocket server = null;
    try {
      server = new ServerSocket(port);
      while (true) {
        System.out.println("Configuration server starts on port " + port);
        ServerConfigWorker srvConfWorker = new ServerConfigWorker();
        srvConfWorker.setSocket(server.accept());
        srvConfWorker.start();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

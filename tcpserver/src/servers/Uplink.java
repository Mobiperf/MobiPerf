package servers;

import java.io.IOException;
import java.net.ServerSocket;

public class Uplink {
  public static void main(String[] argv){
    int port = Definition.PORT_UPLINK;
    ServerSocket server = null;
    try {
      server = new ServerSocket(port);
      while (true) {
        System.out.println("Uplink server starts on port " + port);
        UplinkWorker uplinkWorker = new UplinkWorker();
        uplinkWorker.setSocket(server.accept());
        uplinkWorker.start();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}


package servers;

import common.BaseServer;
import common.Definition;

/**
 * This class is the main class for downlink throughput test, it runs forever unless being killed
 * 
 * @author hjx@umich.edu (Junxian Huang)
 */
public class Downlink extends BaseServer {
	
	public static void main(String[] argv){
		int port = Definition.PORT_MLAB_DOWNLINK;
		while (true) {
			System.out.println("Downlink tcpServer starts on port " + port);
			Downlink server = new Downlink();
			server.listenSocket(port);
		}
	}
}

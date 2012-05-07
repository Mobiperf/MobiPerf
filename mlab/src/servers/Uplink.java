/**
 * @author Junxian Huang
 * @date Aug 30, 2009
 * @time 3:28:53 PM
 * @organization University of Michigan, Ann Arbor
 */

package servers;


import common.BaseServer;
import common.Definition;

/**
 * This class is the main class for uplink throughput test, it runs forever unless being killed
 * 
 * @author hjx@umich.edu (Junxian Huang)
 */
public class Uplink extends BaseServer {
	
	public static void main(String[] argv){
		int port = Definition.PORT_MLAB_UPLINK;
		while (true) {
			System.out.println("Uplink tcpServer starts on port " + port);
			Uplink server = new Uplink();
			server.listenSocket(port);
		}
	}
}
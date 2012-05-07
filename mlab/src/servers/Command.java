/****************************
*
* @Date: Oct 12, 2011
* @Time: 5:00:03 PM
* @Author: Junxian Huang
*
****************************/
package servers;

import common.BaseServer;
import common.Definition;

/**
 * @author Junxian Huang
 *
 */
public class Command extends BaseServer {
	
	public static void main(String[] argv){
		while(true){
			System.out.println("Command tcpServer starts");
			Command server = new Command();
			server.listenSocket(Definition.PORT_COMMAND);
		}
	}
}

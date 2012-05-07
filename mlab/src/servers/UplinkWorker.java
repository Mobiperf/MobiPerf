/**
 * @author Junxian Huang
 * @date Aug 30, 2009
 * @time 3:30:27 PM
 * @organization University of Michigan, Ann Arbor
 */
package servers;

import java.io.IOException;
import java.io.InputStreamReader;

import common.BaseTcpWorker;
import common.Definition;

/**
 * @author Junxian Huang
 * 
 * @Description
 * Uplink tcpServer as both uplink and TCP connect RTT measurement tcpServer for Mlab
 */
public class UplinkWorker extends BaseTcpWorker {

	public long id;
	public int port;

	public UplinkWorker(int port){
		this.port = port;
	}

	public void run() {
		try {
			client.setSoTimeout(Definition.TCP_RECEIVE_TIMEOUT);

			InputStreamReader in = new InputStreamReader(client.getInputStream());			
			char buffer[] = new char[1000 * 1000]; //TODO the buffer size can be discussed

			while (in.read(buffer) > -1) {
				//keeps receiving from client, and eventually would timeout or -1 returned 
			}
			in.close();
			client.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

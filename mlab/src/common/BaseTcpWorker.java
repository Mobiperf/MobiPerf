/**
 * @author Junxian Huang
 * @date Aug 29, 2009
 * @time 4:38:34 PM
 * @organization University of Michigan, Ann Arbor
 */
package common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * @author Junxian Huang
 *
 */
public class BaseTcpWorker extends Thread{
	public Socket client;

	public void setSocket(Socket client){
		this.client = client;
	}


	public void run(){

		try {

			//System.out.println("TCP tcpServer connected " + client.getPort() + " " + client.getInetAddress().getHostAddress());
			client.setSoTimeout(Definition.TCP_RECEIVE_TIMEOUT);

			// String line = "";
			BufferedReader in = null;
			PrintWriter out = null;
			char buffer[] = new char[20480];

			in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			out = new PrintWriter(client.getOutputStream(), true);


			StringBuilder sb = new StringBuilder("");

			int bytes_read = in.read(buffer);
			if(bytes_read >= 0){
				sb.append(buffer, 0, bytes_read);
			}

			String request = sb.toString();
			out.print(request);
			out.flush();
			

			in.close();
			out.close();
			client.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}

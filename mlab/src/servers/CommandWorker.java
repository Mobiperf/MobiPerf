/****************************
 *
 * @Date: Oct 12, 2011
 * @Time: 5:01:01 PM
 * @Author: Junxian Huang
 *
 ****************************/
package servers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import common.BaseTcpWorker;
import common.Definition;
import common.Util;

/**
 * @author Junxian Huang
 *
 */

public class CommandWorker extends BaseTcpWorker{

	public long id;


	public void run() {

		try {

			client.setSoTimeout(Definition.TCP_RECEIVE_TIMEOUT);

			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			PrintWriter out = new PrintWriter(client.getOutputStream(), true);
			char buffer[] = new char[20480];

			int bytes_read;

			StringBuilder report_sb = new StringBuilder("");
			while((bytes_read = in.read(buffer)) > -1){
				//System.out.println("<Thread " + id + "> received " + bytes_read + " bytes");
				report_sb.append(buffer, 0, bytes_read);
			}

			String report = report_sb.toString().trim(); //remove the /r/n in the end

			String root_dir = Util.runCmd("pwd", false) + "/";
			System.out.println("Current directory: " + root_dir);// /home/hjx/mobiperf/

			String ip = client.getInetAddress().getHostAddress();
			int port = 0;

			String cmd = "";
			//TODO specify command
			if(report.equals(Definition.COMMAND_MLAB_INIT_DOWNLINK)){
				port = Definition.PORT_MLAB_DOWNLINK;

			}else if(report.equals(Definition.COMMAND_MLAB_INIT_UPLINK)){
				port = Definition.PORT_MLAB_UPLINK;

			}else if(report.equals(Definition.COMMAND_MLAB_END_DOWNLINK)){
				port = Definition.PORT_MLAB_DOWNLINK;

			}else if(report.equals(Definition.COMMAND_MLAB_END_UPLINK)){
				port = Definition.PORT_MLAB_UPLINK;

			}else if(report.startsWith("RUBBISH:")){
				//this is just for keep alive, ignore 
				in.close();
				out.close();
				client.close();
				return;//return here, don't need to write command into output
			}else{
				System.out.println("Command not support <" + report + ">");
				in.close();
				out.close();
				client.close();
				return;
			}

			String res = Util.runCmd(cmd, true);
			System.out.println("Command worker: " + res);


		} catch (Exception e) {
			try {
				if (client != null) {
					client.close();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		}
	}


}

/****************************
*
* @Date: Mar 14, 2011
* @Time: 1:46:46 PM
* @Author: Junxian Huang
* 
****************************/
package servers;

import java.util.Arrays;
import java.util.HashMap;

import common.*;

public class Reach extends BaseServer {
	
	public static HashMap<Integer, String> port_map = new HashMap<Integer, String>();
	
	public static void PrintSupportedPorts() {
		System.out.println("Supported ports:");
		Object[] keys = port_map.keySet().toArray();
		Arrays.sort(keys);
		for (int i = 0 ; i < keys.length ; i++) {
			int key = (Integer)keys[i];
			System.out.print(port_map.get(key) + " " + key);
			if (key == 80 || key == 22) {
				System.out.print(" (using default tcpServer)");
			}
			System.out.println("");
		}
	}
	
	public static void main(String[] argv) {
		
		//only allow specific ports
		//bittorrent port added
		
		port_map.put(Definition.PORT_FTP, "FTP");
		port_map.put(Definition.PORT_SSH, "SSH");
		port_map.put(Definition.PORT_SMTP, "SMTP");
		port_map.put(Definition.PORT_DNS, "DNS");
		port_map.put(Definition.PORT_HTTP, "HTTP");
		port_map.put(Definition.PORT_POP, "POP");
		port_map.put(Definition.PORT_RPC, "RPC");
		port_map.put(Definition.PORT_NETBIOS, "NETBIOS");
		port_map.put(Definition.PORT_IMAP, "IMAP");
		port_map.put(Definition.PORT_SNMP, "SNMP");
		port_map.put(Definition.PORT_HTTPS, "HTTPS");
		port_map.put(Definition.PORT_SMB, "SMB");
		port_map.put(Definition.PORT_SMTP_SSL, "SMTP SSL");
		port_map.put(Definition.PORT_SECURE_IMAP, "SECURE IMAP");
		port_map.put(Definition.PORT_AUTHENTICATED_SMTP, "AUTHENTICATED SMTP");
		port_map.put(Definition.PORT_IMAP_SSL, "IMAP SSL");
		port_map.put(Definition.PORT_POP_SSL, "POP SSL");
		port_map.put(Definition.PORT_SIP, "SIP");
		port_map.put(Definition.PORT_BITTORRENT, "BITTORRENT");
		port_map.put(Definition.PORT_IOS_SPECIAL, "IOS SPECIAL");
		port_map.put(Definition.PORT_ANDROID_SPECIAL, "ANDROID SPECIAL");
		port_map.put(Definition.PORT_HTTP_PROXY, "HTTP PROXY");
		
		//check input
		if(argv.length != 2){
			System.out.println("Usage: java -jar Reach.jar port udp|tcp");
			PrintSupportedPorts();
			System.exit(-1);
		}
		
		Util.printWelcome("Reachability<" + argv[0] + ":" + argv[1] + ">");
		
		int port = Integer.parseInt(argv[0]);
		
		//whether is supported port
		if(!port_map.containsKey(port)){
			System.out.println(port + " is not supported by MobiPerf Reachability tcpServer");
			PrintSupportedPorts();
			System.exit(-2);
		}
		
		//whether the protocol is supported
		String protocol = argv[1];
		if(protocol.equalsIgnoreCase("tcp") || protocol.equalsIgnoreCase("udp")){
		}else{
			System.out.println(protocol + " is not support by MobiPerf Reachability tcpServer, only TCP or UDP is supported");
			System.exit(-3);
		}
		
		/*//http/ssh port excluded because http/ssh tcpServer needs to be run
		if(port == Definition.PORT_HTTP || port == Definition.PORT_SSH || port == Definition.PORT_HTTPS){
			System.out.println(port_map.get(port) + " " + port + " is using default tcpServer");
			System.out.println("Be sure that your client does not check the echo message, " +
					"because for HTTP/SSH/HTTPS servers, " +
					"the client will receive a typical message " +
					"no matter what you send to the tcpServer");
			System.exit(-4);
		}//*/
				
		
		if(protocol.equalsIgnoreCase("tcp")){
			while(true){
				System.out.println("Reachability tcpServer starts: " + port_map.get(port) + " " + port);
				Reach server = new Reach();
				server.listenSocket(port);
			}
		}else if(protocol.equalsIgnoreCase("udp")){
			while(true){
				System.out.println("Reachability tcpServer starts: " + port_map.get(port) + " " + port);
				Reach server = new Reach();
				server.receiveUdpPacket(port);
			}
		}
	}
}

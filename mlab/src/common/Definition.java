/**
 * @author Junxian Huang
 * @date Aug 29, 2009
 * @time 1:55:16 PM
 * @organization University of Michigan, Ann Arbor
 */
package common;

/**
 * @author Junxian Huang
 *
 */
public class Definition {
	
	public static final String MAIN_SERVER = "mobiperf.com";
	
	public static final int THROUGHPUT_DOWN_SEGMENT_SIZE = 2600;
	
	public static final long THROUGHPUT_DURATION = 20000;
	public static final int TCP_RECEIVE_TIMEOUT = 15000;
	
	public static final int PORT_MLAB_DOWNLINK = 6001;
	public static final int PORT_MLAB_UPLINK = 6002;
	public static final int PORT_COMMAND = 5010;
	
	
	public static final int PORT_FTP = 21;
	public static final int PORT_SSH = 22;
	public static final int PORT_SMTP = 25;
	public static final int PORT_DNS = 53;
	public static final int PORT_HTTP = 80;
	public static final int PORT_POP = 110;
	public static final int PORT_RPC = 135;
	public static final int PORT_NETBIOS = 139;
	public static final int PORT_IMAP = 143;
	public static final int PORT_SNMP = 161;
	public static final int PORT_HTTPS = 443;
	public static final int PORT_SMB = 445;
	public static final int PORT_SMTP_SSL = 465;
	public static final int PORT_SECURE_IMAP = 585;
	public static final int PORT_AUTHENTICATED_SMTP = 587;
	public static final int PORT_IMAP_SSL = 993;
	public static final int PORT_POP_SSL = 995;
	public static final int PORT_SIP = 5060;
	public static final int PORT_BITTORRENT = 6881;
	public static final int PORT_IOS_SPECIAL = 5223;
	public static final int PORT_ANDROID_SPECIAL = 5228;
	public static final int PORT_HTTP_PROXY = 8080;
	
	public static final int[] PORTS = new int[]{21, 22, 25, 53, 80, 110, 135, 139, 143, 161,
		443, 445, 465, 585, 587, 993, 995, 5060, 6881, 5223, 5228, 8080};

	
	//command 
	public static final String COMMAND_TCP_UPLINK = "COMMAND:TCP:UPLINK";
	public static final String COMMAND_TCP_DOWNLINK = "COMMAND:TCP:DOWNLINK";
	
	public static final String COMMAND_MLAB_INIT_UPLINK = "COMMAND:MLAB:INIT:UPLINK";
	public static final String COMMAND_MLAB_INIT_DOWNLINK = "COMMAND:MLAB:INIT:DOWNLINK";
	public static final String COMMAND_MLAB_END_UPLINK = "COMMAND:MLAB:END:UPLINK";
	public static final String COMMAND_MLAB_END_DOWNLINK = "COMMAND:MLAB:END:DOWNLINK";
	
	public static final String COMMAND_REACH_START = "COMMAND:REACH:START";
	public static final String COMMAND_REACH_STOP = "COMMAND:REACH:STOP";
	

}

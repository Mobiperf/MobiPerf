/****************************
 * This file is part of the MobiPerf project (http://mobiperf.com). 
 * We make it open source to help the research community share our efforts.
 * If you want to use all or part of this project, please give us credit and cite MobiPerf's official website (mobiperf.com).
 * The package is distributed under license GPLv3.
 * If you have any feedbacks or suggestions, don't hesitate to send us emails (3gtest@umich.edu).
 * The server suite source code is not included in this package, if you have specific questions related with servers, please also send us emails
 * 
 * Contact: 3gtest@umich.edu
 * Development Team: Junxian Huang, Birjodh Tiwana, Zhaoguang Wang, Zhiyun Qian, Cheng Chen, Yutong Pei, Feng Qian, Qiang Xu
 * Copyright: RobustNet Research Group led by Professor Z. Morley Mao, (Department of EECS, University of Michigan, Ann Arbor) and Microsoft Research
 *
 ****************************/

/****************************
 *
 * @Date: Mar 23, 2011
 * @Time: 3:04:02 PM
 * @Author: Junxian Huang
 *
 ****************************/
package com.mobiperf.ui;


/**
 * 
 * All constant definitions should come here
 *
 */
public class Definition {

	//change this to be the server you will connect to, either IP or domain name is fine
	public static final String SERVER_NAME = "falcon.eecs.umich.edu";
	public static boolean DEBUG = false;
	public static boolean TEST = false;

	//DON'T CHANGE BELOW
	//port definitions
	public static final int PORT_WHOAMI = 5000;

	public static final int PORT_DOWNLINK = 5001;
	public static final int PORT_UPLINK = 5002;
	//MLab nodes, the above two ports are already occupied = =!
	public static final int PORT_DOWNLINK_MLAB = 6001;
	public static final int PORT_UPLINK_MLAB = 6002;
	
	public static final int PORT_CONTROL = 5004;
	public static final int PORT_TCPDUMP_REPORT = 5006;
	public static final int PORT_COMMAND = 5010;

	public static final int PORT_DNS = 53;
	public static final int PORT_BT = 6881;
	public static final int PORT_BT_RAND = 5005;
	public static final int PORT_HTTP = 80;


	public static final int TP_DURATION_IN_MILLI = 16000; // 16 seconds for throughput tests
	public static final int TCP_TIMEOUT_IN_MILLI = 10000; // 5 seconds for timeout
	public static final int UDP_TIMEOUT_IN_MILLI = 10000;

	public static final int IP_HEADER_LENGTH = 20;
	public static final int TCP_HEADER_LENGTH = 32;
	public static final int UDP_HEADER_LENGTH = 8;
	public static final int PREFIX_RECEIVE_BUFFER_LENGTH = 1000;
	public static final int TCPDUMP_RECEIVE_BUFFER_LENGTH = 1000000;
	
	public static final int THROUGHPUT_UP_SEGMENT_SIZE = 1300;
	public static final int THROUGHPUT_DOWN_SEGMENT_SIZE = 2600;

	public static final int GPS_UPDATE_WAITING_TIME = 10000; //wait for 10 seconds for GPS to updated


	public static final String TYPE = "android";
	public static final String RESULT_DELIMITER = "-_hjx-_";

	//periodic related
	public static final int PERIODIC_REQUEST_CODE = 100000;
	public static final long PERIODIC_INTERVAL = 60 * 60 * 1000;
	public static final long PERIODIC_FIRST_RUN_STARTING_DELAY = 5 * 60 * 1000;
	public static final String PERIODIC_FILE = "periodic_file";

	//port scanning
	public static int[] PORTS = new int [] {21, 22, 25, 53, /*80,*/
		110, 135, 139, 143, 161, 
		/*443,*/ 445, 465, 585, 587, 
		993, 995, 5060, 6881, 5223, 
		5228, 8080};
	public static final String[] PORT_NAMES = new String[] {"FTP", "SSH", "SMTP", "DNS", /*"HTTP",*/
		"POP", "RPC", "NETBIOS", "IMAP", "SNMP", 
		/*"HTTPS",*/  "SMB", "SMTP SSL", "Secure IMAP", "Auth SMTP", 
		"IMAP SSL", "POP SSL", "SIP", "BITTORRENT", "IOS SPECIAL",
		"ANDROID SPECIAL", "HTTP PROXY"};

	//report
	public static final int REPORT_MAX_PLAINTEXT_LENGTH = 10000;

	//command 
	public static final String COMMAND_TCP_UPLINK = "COMMAND:TCP:UPLINK";
	public static final String COMMAND_TCP_DOWNLINK = "COMMAND:TCP:DOWNLINK";
	public static final String COMMAND_REACH_START = "COMMAND:REACH:START";
	public static final String COMMAND_REACH_STOP = "COMMAND:REACH:STOP";
	
	public static final String COMMAND_MLAB_INIT_UPLINK = "COMMAND:MLAB:INIT:UPLINK";
	public static final String COMMAND_MLAB_INIT_DOWNLINK = "COMMAND:MLAB:INIT:DOWNLINK";
	public static final String COMMAND_MLAB_END_UPLINK = "COMMAND:MLAB:END:UPLINK";
	public static final String COMMAND_MLAB_END_DOWNLINK = "COMMAND:MLAB:END:DOWNLINK";
	
}

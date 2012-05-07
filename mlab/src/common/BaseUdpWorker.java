/****************************
 *
 * @Date: Mar 28, 2011
 * @Time: 11:33:33 PM
 * @Author: Junxian Huang
 *
 ****************************/
package common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * 
 * @author junxianhuang
 * 
 * Handle an incoming UDP packet, simply send back response
 * 
 */
public class BaseUdpWorker extends Thread{

	private DatagramSocket socket;
	private DatagramPacket packet;

	public BaseUdpWorker(DatagramSocket socket, DatagramPacket packet){
		this.socket = socket;
		this.packet = packet;
	}
	
	//this is a sample function for echoing whatever sent to server
	@Override
	public void run(){
		try {
			String echo = new String(packet.getData());
			DatagramPacket p2 = new DatagramPacket(echo.getBytes(), echo.length(),
					packet.getAddress(), packet.getPort());
			socket.send(p2);
			//socket.close(); //don't need to close because we are reusing
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

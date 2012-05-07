
package servers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Random;

import common.Util;

/**
 * This class checkes whether other MLab related server programes are running properly
 * and reports to the central controller periodically
 * 
 * @author hjx@umich.edu (Junxian Huang)
 */
public class KeepAlive {

	public static final long SLEEP_TIME = 10 * 60 * 1000;

	public static String ip;

	public static void main(String argv[]){
		System.out.println("Keep alive for MobiPerf throughput tcpServer node list");
		while(true){
			//TODO check the status of other servers
			getMyIp();
			sendKeepAlive();
			try {
				Thread.sleep(SLEEP_TIME + ((new Random()).nextLong() % (5 * 60 * 1000))); 
				//randomized 5 minutes to lower the tcpServer's burden
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * TODO this function should be changed to use an API from central controller to get it's own global IP
	 */
	public static void getMyIp(){
		try {
			// Send data
			URL url = new URL("http://mobiperf.com/php/myip.php");
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);

			// Get the response
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				ip = line.trim();
			}
			rd.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void sendKeepAlive(){

		try {
			String host = Util.getCurrentHost();
			double lat = 0, lon = 0;
			if(host.equals("mobiperf.com")){
				lat = 42.292301;
				lon = -83.7145;
			}else{
				//in the format of mlab2.lga02.measurement-lab.org, where lga is the closest airport
				String airport = host.substring(6, 9);
				if(airport.equals("ams")){
					//Amsterdam, Netherlands
					lat = 52.308613;
					lon = 4.763889;
				}else if(airport.equals("ath")){
					//Athens, Greece
					lat = 37.936358;
					lon = 23.944467;
				}else if(airport.equals("atl")){
					//Atlanta, Georgia, United States
					lat = 33.636720;
					lon = -84.428065;
				}else if(airport.equals("dfw")){
					//Dallas-Fort Worth, Texas, United States
					lat = 32.896828;
					lon = -97.037995;
				}else if(airport.equals("ham")){
					//Hamburg, Hamburg, Germany
					lat = 53.630389;
					lon = 9.988228;
				}else if(airport.equals("hnd")){
					//Tokyo, Honshu, Japan
					lat = 35.553333;
					lon = 139.781111;
				}else if(airport.equals("iad")){
					//Washington, District of Columbia, United States
					lat = 38.947444;
					lon = -77.459943;
				}else if(airport.equals("lax")){
					//Los Angeles, California, United States
					lat = 33.942522;
					lon = -118.407160;
				}else if(airport.equals("lga")){
					//New York, New York, United States
					lat = 40.777250;
					lon = -73.872610;
				}else if(airport.equals("lhr")){
					//London, Middlesex, England, United Kingdom
					lat = 51.477500;
					lon = -0.461388;
				}else if(airport.equals("mia")){
					//Miami, Florida, United States
					lat = 25.795361;
					lon = -80.290110;
				}else if(airport.equals("nuq")){
					//Mountain View, California, United States
					//http://www.gcmap.com/airport/NUQ
					lat = 37.416139;
					lon = -122.049138;
				}else if(airport.equals("ord")){
					//Chicago, Illinois, United States
					lat = 41.981649;
					lon = -87.906670;
				}else if(airport.equals("par")){
					//Paris, France
					lat = 48.867;
					lon = 2.333;
				}else if(airport.equals("sea")){
					//Seattle, Washington, United States
					lat = 47.449889;
					lon = -122.311777;
				}else if(airport.equals("syd")){
					//Sydney, New South Wales, Australia
					lat = -33.946110;
					lon = 151.177222;
				}else if(airport.equals("wlg")){
					//Wellington, New Zealand
					lat = -41.327221;
					lon = 174.805278;
				}else{
					lat = 0;
					lon = 0;
				}

			}
			// Construct data
			String data = URLEncoder.encode("ip", "UTF-8") + "=" + URLEncoder.encode(ip, "UTF-8"); 
			data += "&" + URLEncoder.encode("host", "UTF-8") + "=" + URLEncoder.encode(host, "UTF-8");
			data += "&" + URLEncoder.encode("lat", "UTF-8") + "=" + lat;
			data += "&" + URLEncoder.encode("lon", "UTF-8") + "=" + lon;

			// Send data
			URL url = new URL("http://mobiperf.com/php/keepalive.php");
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(data);
			wr.flush();

			// Get the response
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			while ((line = rd.readLine()) != null) {
				System.out.println((System.currentTimeMillis() / 1000.0) + ": " + line); 
			}
			wr.close();
			rd.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

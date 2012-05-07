/****************************
*
* @Date: Mar 16, 2011
* @Time: 11:44:14 PM
* @Author: Junxian Huang
*
****************************/
package common;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Random;

public class Util {
	
	public static void printWelcome(String serverName){
		System.out.println("***********************************************");
		System.out.println("* Welcome to MobiPerf tcpServer");
		System.out.println("* Server name: " + serverName);
		System.out.println("***********************************************");
	}
	
	
	public static String getCurrentHost() throws IOException{
		return Util.runCmd("uname -a", false).split(" ")[1];
	}
	
	/**
	 * (synchronized)
	 * Multi threads wrting to a single file
	 * you should take care of line breaks
	 * 
	 * open filewriter but does not close
	 * 
	 * Bug: the last 10000 bytes may not be written, hard to solve this bug actually because hard to determine the end
	 * 
	 */
	public static FileWriter fw = null;
	public static BufferedWriter bw = null;
	public static StringBuilder buffer = new StringBuilder("");
	public static synchronized void writeToFileWithMutex(String filename, String content){
		
		buffer.append(content);
		
		//if buffer not full, return fast
		if (buffer.length() < 10000) {
			return;
		}	
		
		try {
			if(fw == null) {
				fw = new FileWriter(filename, true);
				bw = new BufferedWriter(fw);
			}
			bw.write(buffer.toString());
			bw.flush();
			buffer = new StringBuilder("");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Run a system command
	 * @param cmd
	 * @return standard output in a single string at most 50000 chars
	 * @throws IOException
	 */
	public static String runCmd(String cmd, boolean sudo) throws IOException{
		if(sudo)
			cmd = "sudo " + cmd;
		
		System.out.println("Run CMD: " + cmd);
		Process p = Runtime.getRuntime().exec(cmd);
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

		String s = null;
		String res = "";
		while((s = stdInput.readLine()) != null){//is there any problem for single lined output???
			res += s + "\n";
		}
		//char[] buffer = new char[50000];
		//stdInput.read(buffer);
		//String res = new String(buffer); 
		
		//System.out.println("Command " + cmd);
		//System.out.println("Result " + res);
		
		p.destroy();
		return res.trim();
	}
	
	public static boolean postToPhp(String type, String deviceId, String rid, String name, String field, String value){
		try {
		    // Construct data
		    String data = URLEncoder.encode("type", "UTF-8") + "=" + URLEncoder.encode(type, "UTF-8");
		    data += "&" + URLEncoder.encode("deviceId", "UTF-8") + "=" + URLEncoder.encode(deviceId, "UTF-8");
		    data += "&" + URLEncoder.encode("rid", "UTF-8") + "=" + URLEncoder.encode(rid, "UTF-8");
		    data += "&" + URLEncoder.encode("name", "UTF-8") + "=" + URLEncoder.encode(name, "UTF-8");
		    data += "&" + URLEncoder.encode("field", "UTF-8") + "=" + URLEncoder.encode(field, "UTF-8");
		    data += "&" + URLEncoder.encode("value", "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");

		    // Send data
		    URL url = new URL("http://mobiperf.com/php/report.php");
		    URLConnection conn = url.openConnection();
		    conn.setDoOutput(true);
		    OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
		    wr.write(data);
		    wr.flush();

		    // Get the response
		    BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    //String line;
		    while ((/*line = */rd.readLine()) != null) {
		    }
		    wr.close();
		    //rd.close();
		    return true;
		} catch (Exception e) {
		}
		return false;
	}
	
	/**
	 * Calculate spherical distance based on the GPS of two points based on Haversine formula
	 * @param lat1
	 * @param lon1
	 * @param lat2
	 * @param lon2
	 * @return Distance in the great circle in miles
	 */
	public static double sphericalDistance(double lat1, double lon1, double lat2, double lon2){
	 	double earthRadius = 3958.75;
	    double dLat = Math.toRadians(lat2-lat1);
	    double dLng = Math.toRadians(lon2-lon1);
	    double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
	               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
	               Math.sin(dLng/2) * Math.sin(dLng/2);
	    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
	    double dist = earthRadius * c;
	
	    //int meterConversion = 1609;
	
	    return dist;//new Float(dist /** meterConversion*/).floatValue();
	}
	
	public static String genRandomString(int len){
		StringBuilder sb = new StringBuilder("");
		Random ran = new Random();
		for(int i = 1; i <= len; i++){
			sb.append((char)('a' + ran.nextInt(26)));
		}
		return sb.toString();
	}

}

package com.google.wireless.speed.speedometer;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Utility class for Speedometer.
 * 
 * @author mdw@google.com (Matt Welsh)
 */
public class Util {
  private static DateFormat dateFormat = new SimpleDateFormat(
      "yyyy-MM-dd'T'HH:mm:ss");
  static {
    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }
  
  public static Date parseDate(String dateString) throws ParseException {
    return dateFormat.parse(dateString);
  }
  
  public static String formatDate(Date date) {
    return dateFormat.format(date);
  }

}

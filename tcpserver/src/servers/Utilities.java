package servers;

import java.util.Random;

public class Utilities {
  static Random ran = new Random();

  public static void genRandomByteArray(byte[] byteArray) {
    for (int i = 0; i < byteArray.length; i++) {
      byteArray[i] = (byte)('a' + ran.nextInt(26));
    }
  }

  public static String[] parsePrefix(String prefix){
    String[] prefix_array = prefix.trim().split("<");

    String[] result = new String[prefix_array.length - 1];
    for(int i = 0; i < prefix_array.length - 1; i++){
      result[i] = prefix_array[i + 1].split(">")[0];
    }
    
    if(result.length >= 3)
      return result;
    else
      return null;
  }
}

package Utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hashing {
    synchronized public static int hash(String string) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(string.getBytes());
        } catch (NoSuchAlgorithmException e){return -1;}
        byte[] digest = messageDigest.digest();
        String stringHash = new String(digest);
        SynchronizedPrint.printHex(digest);
        System.out.println();
        //System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(stringHash.getBytes()));
        long max = 2147483647;
        long min = -2147483648;
        return (int)(((long)stringHash.hashCode()+max)*(32768.0/(max+Math.abs(min))));
    }
}

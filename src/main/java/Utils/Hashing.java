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
        //String stringHash = new String(digest);
        //SynchronizedPrint.printHex(digest);
        //System.out.println();
        //shrink the byte array to an int using xor
        short hash = bytesToShort(digest);
        hash &= 0x7FFF; //remove sign bit
        return hash;
        /*
        //System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(stringHash.getBytes()));
        long max = 2147483647;
        long min = -2147483648;
        return (int)(((long)stringHash.hashCode()+max)*(32768.0/(max+Math.abs(min))));
        */
    }

    private static int bytesToInt(byte[] bytes) {
        int hash = 0;
        for (int i = 0; i < bytes.length/4; i++) {
            hash ^= bytes[4*i+0] << 0;
            hash ^= bytes[4*i+1] << 8;
            hash ^= bytes[4*i+2] << 16;
            hash ^= bytes[4*i+3] << 24;
        }
        return hash;
    }
    private static short bytesToShort(byte[] bytes) {
        short hash = 0;
        for (int i = 0; i < bytes.length/2; i++) {
            hash ^= bytes[2*i+0] << 0;
            hash ^= bytes[2*i+1] << 8;
        }
        return hash;
    }
}

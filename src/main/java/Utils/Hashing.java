package Utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hashing {
    synchronized public static int hash(String string) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(string.getBytes());
        } catch (NoSuchAlgorithmException e){return -1;}
        byte[] digest = messageDigest.digest();
        //shrink the byte array to an int using xor
        short hash = bytesToShort(digest);
        hash &= 0x7FFF; //remove sign bit
        return hash;
    }

    private static short bytesToShort(byte[] bytes) {
        byte byte0 = 0;
        byte byte1 = 0;
        for (int i = 0; i < bytes.length/2; i++) {
            byte0 ^= bytes[2 * i];
            byte1 ^= bytes[2 * i + 1];
        }
        return (short)((byte0 & 0xFF) | (byte1 & 0xFF) << 8);
    }
}

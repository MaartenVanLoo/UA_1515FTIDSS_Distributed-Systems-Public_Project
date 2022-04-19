package Utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

public class IPUtils {

    /**
     * Prints the IPv4 network interfaces of the machine you're using to the console.
     * @throws SocketException
     */
    public static void printIpv4Interfaces() throws SocketException {
        Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
        while(e.hasMoreElements())
        {
            NetworkInterface n = e.nextElement();
            Enumeration<InetAddress> ee = n.getInetAddresses();
            while (ee.hasMoreElements())
            {
                InetAddress i = ee.nextElement();
                if (i.getClass() == Inet4Address.class){ //filter for ipv4
                    System.out.printf("%-40s\t",n.getDisplayName());
                    System.out.println(i.getHostAddress());
                }
            }
        }
    }

    public static  ArrayList<InetAddress> getIpv4BroadcastAdresses() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        ArrayList<InetAddress> broadcastAddresses = new ArrayList<InetAddress>();
        while(interfaces.hasMoreElements())
        {
            NetworkInterface networkInterface = interfaces.nextElement();
            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements())
            {
                InetAddress inetAddress = inetAddresses.nextElement();
                if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress())
                {
                    // filter for ipv4
                    //get broadcast address
                    broadcastAddresses.add(networkInterface.getInterfaceAddresses().get(0).getBroadcast());
                }
            }
        }
        return broadcastAddresses;
    }
}
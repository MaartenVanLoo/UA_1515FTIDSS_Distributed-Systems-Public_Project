package Client;
import Utils.Hashing;
import Utils.IPUtils;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;

public class Client {
    public static void main(String[] args) throws UnknownHostException, SocketException {
         System.out.println("Client Started");
         System.out.println(Hashing.hash("Node0"));
         System.out.println(Hashing.hash("Node1"));
         System.out.println(Hashing.hash("Node2"));

        InetAddress.getAllByName("localhost");
        for (InetAddress address : InetAddress.getAllByName("localhost")) {
            System.out.println(address.getHostAddress());
        }
        System.out.println("Broadcast addresses: ");
        ArrayList<InetAddress> arrayList =  IPUtils.getIpv4BroadcastAdresses();
        for (InetAddress address : arrayList) {
            System.out.println(address.getHostAddress());
        }
    }


}

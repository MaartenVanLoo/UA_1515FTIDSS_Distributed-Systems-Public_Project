package Client;
import Utils.Hashing;
import Utils.IPUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;

public class Client {
    private Socket nodeTCPSocket;
    private PrintWriter out;
    private BufferedReader in;

    //TCP connection
    public void startTCPConnection(String ip, int port) throws IOException {
        nodeTCPSocket = new Socket(ip, port);
        out = new PrintWriter(nodeTCPSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(nodeTCPSocket.getInputStream()));
    }

    public String sendMessage(String message) throws IOException {
        out.println(message);
        return in.readLine(); //Return response;
    }

    public void stopTCPConnection() throws IOException {
        in.close();
        out.close();
        nodeTCPSocket.close();
    }

    //TCP debug method
    public void sendTCPMessage() throws IOException {
        startTCPConnection("192.168.48.4", 8001); //misschien 8001?node3
        String message = "Hello from Client to NODE3";
        System.out.printf("Sending message: %s\n", message);
        String response = sendMessage(message);
        System.out.printf("Response: %s\n", response);
        stopTCPConnection();
    }
    public static void main(String[] args) throws IOException {
        //TCP
        Client client = new Client();
        //client.sendTCPMessage();
        //initilize arraylist with possible hostnames
        String[] hostnames = new String[]{
                "Node0",
                "Node1",
                "Node2",
                "Node3",
                "Node4",
                "Host0",
                "Host1",
                "Host2",
                "Host3",
                "Host4",
                "node0",
                "node1",
                "node2",
                "node3",
                "node4",
                "host0",
                "host1",
                "host2",
                "host3",
                "host4"
        };
        String[] filenames = new String[]{
                "Caffeine.png",
                "ItWorks.jpg",
                "GodKnows.jpg",
                "ProgramWithAnAttitude.jpg",
                "CodingFun.jpg",
                "magicCode.jpg",
                "NoPlaceLike127.png",
                "TrustMeIamAProgrammer.jpg"
        };

        String prefix = ".6dist";
        for (String hostname : hostnames) {
            System.out.println(hostname + prefix + "\t" + Hashing.hash(hostname+prefix));
        }
        for (String filename : filenames) {
            System.out.println(String.format("%30s\t%s",filename,Hashing.hash(filename)));
        }
        ;
       /*
        String test = "Node0";
        for (byte theByte : test.getBytes(StandardCharsets.UTF_8))
        {
            System.out.print(Integer.toHexString(theByte));
            System.out.print(" ");
        }
        System.out.println();

        InetAddress.getAllByName("localhost");
        for (InetAddress address : InetAddress.getAllByName("localhost")) {
            System.out.println(address.getHostAddress());
        }
        System.out.println("Broadcast addresses: ");
        ArrayList<InetAddress> arrayList =  IPUtils.getIpv4BroadcastAdresses();
        for (InetAddress address : arrayList) {
            System.out.println(address.getHostAddress());
        }*/
    
    }

}

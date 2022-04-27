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
        client.sendTCPMessage();
        /*System.out.println("Client Started");
        System.out.println("Node0\t" + Hashing.hash("Node0"));
        System.out.println("Node1\t" + Hashing.hash("Node1"));
        System.out.println("Node2\t" + Hashing.hash("Node2"));
        System.out.println("Node3\t" + Hashing.hash("Node3"));
        System.out.println("Node4\t" + Hashing.hash("Node4"));
        System.out.println("Node5\t" + Hashing.hash("Node5"));
        System.out.println("Node6\t" + Hashing.hash("Node6"));
        System.out.println("Node7\t" + Hashing.hash("Node7"));
        System.out.println("Node8\t" + Hashing.hash("Node8"));
        System.out.println("Node9\t" + Hashing.hash("Node9"));
        System.out.println("Node10\t" + Hashing.hash("Node10"));

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
        }
    }*/
    }

}

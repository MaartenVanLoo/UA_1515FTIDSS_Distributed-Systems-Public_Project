package Client;
import Agents.FailureAgent;
import Utils.Hashing;
import Utils.IPUtils;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.TreeMap;

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
    public static void main(String[] args) throws IOException, ClassNotFoundException {
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
                "magicCode.jpg",
                "MagicCode.jpg",
                "NoPlaceLik127.jpg",
                "NoPlaceLik127.0.0.1.jpg",
        };

        String prefix = ".6dist";
        for (String hostname : hostnames) {
            System.out.println(hostname + prefix + "\t" + Hashing.hash(hostname+prefix));
        }
        for (String filename : filenames) {
            System.out.println(filename + "\t" + Hashing.hash(filename));
        }

        //test somethings with maps
        HashMap<String, Boolean> fileLocks = new HashMap<>();
        fileLocks.put("file1",true);
        fileLocks.put("file2",false);
        if (fileLocks.get("file1")) System.out.println("file1 locked");
        if (fileLocks.get("file2")) System.out.println("file2 locked");
        if (fileLocks.containsKey("file3")){
            if (fileLocks.get("file3")) System.out.println("file3 locked");
        }


        //test failure agent
        FailureAgent failureAgent = new FailureAgent(100,new TreeMap<>());
        String agent = failureAgent.serialize();
        System.out.println(agent);
        for (byte b : agent.getBytes()) {
            System.out.print(b + " ");
        }

        FailureAgent failureAgent2 = (FailureAgent.deserialize(agent));
    }

}

package Node;

import com.mashape.unirest.http.Unirest;
import java.io.IOException;
import java.net.*;
import java.nio.file.AccessDeniedException;

public class Node {
    private String ip;
    private final String name;
    private int id;
    private String NS_ip;
    private String NS_port;

    public Node(String name) {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.http");
        root.setLevel(ch.qos.logback.classic.Level.OFF);
        this.name = name;
    }
    
    // Send broadcasts until the NS answers
    public void discoverNameServer() throws IOException {
        InetAddress broadcastIp = InetAddress.getByName("255.255.255.255");
        String message = name;
        boolean received = false;


        DatagramSocket socket = new DatagramSocket(8000);
        socket.setSoTimeout(1000);
        DatagramPacket discoveryPacket = new DatagramPacket(message.getBytes(), message.length(),
                broadcastIp, 8001);
        byte[] response = new byte[32];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        while (!received) {
            // Discovery request command
            socket.send(discoveryPacket);
            System.out.println("Discovery package sent!" + discoveryPacket.getAddress() + ":" + discoveryPacket.getPort());

            // Discovery response command
            try {
                socket.receive(responsePacket);
                System.out.println("Discovery response received!" + responsePacket.getAddress() + ":" + responsePacket.getPort());
                System.out.println(responsePacket.getSocketAddress());
                String responseData = new String(responsePacket.getData()).trim();
                System.out.println("Response:" + responseData);

                if (responseData.equals("Access Denied")){
                    throw new AccessDeniedException("Access to network denied by nameserver");
                }

                this.ip = String.valueOf(responsePacket.getSocketAddress().toString().split("/")[1].split(":")[0]);
                this.id = Integer.parseInt(responseData);
                this.NS_ip = String.valueOf(responsePacket.getAddress().getHostAddress());
                this.NS_port = String.valueOf(responsePacket.getPort());
                received = true;
            } catch (SocketTimeoutException ignored) {
            }
        }
    }

    public void getFileLocation(String filename) {
        try {
            String url = "http://" + this.NS_ip + ":8081/ns/getFile?fileName="+filename;
            System.out.println(Unirest.get(url).asString().getBody());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void terminate(){
        try {
            String url = "http://" + this.NS_ip + ":8081/ns/removeNode?Id=" +this.id;
            System.out.println(Unirest.delete(url).asString().getBody());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void printStatus(){
        System.out.println("Node ip:     \t" + this.ip);
        System.out.println("Node id:     \t" + this.id);
        System.out.println("Node ns ip:  \t" + this.NS_ip);
        System.out.println("Node ns port:\t" + this.NS_port);
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Starting Node");
        String name;
        if (args.length > 0) {
            name = args[0];
        } else {
            name = "default node";
        }

        System.out.println("Network interfaces:");
        System.out.println(NetworkInterface.getNetworkInterfaces());
        Node node = new Node(name);
        node.discoverNameServer();
        node.printStatus();
        InetAddress ip = InetAddress.getLocalHost();
        String hostname = ip.getHostName();
        System.out.println("Your current IP address : " + ip);
        System.out.println("Your current Hostname : " + hostname);
        /*for (int i = 0 ; i <  1000; i++){
            node.getFileLocation("test.txt");
            node.getFileLocation("test1.txt");
            node.getFileLocation("test2.txt");
            node.getFileLocation("test3.txt");
            node.getFileLocation("test4.txt");
        }*/
        for (int i = 0; i < 10;i++) {
            Thread t = new Thread(() -> {
                for (int j = 0; j < 10000; j++) {
                    node.getFileLocation("test.txt");
                    node.getFileLocation("test1.txt");
                    node.getFileLocation("test2.txt");
                    node.getFileLocation("test3.txt");
                    node.getFileLocation("test4.txt");

                }
            });
            t.start();
        }
        node.terminate();
    }
}

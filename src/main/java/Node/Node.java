package Node;

import java.io.IOException;
import java.net.*;

public class Node {
    private String ip;
    private String name;
    private int id;
    private String NS_ip;
    private String NS_port;

    public Node(String name) {
        this.name = name;
    }

    // sends broadcasts until the NS answers
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

                this.ip = String.valueOf(socket.getInetAddress());
                this.id = Integer.parseInt(responseData);
                this.NS_ip = String.valueOf(responsePacket.getAddress());
                this.NS_port = String.valueOf(responsePacket.getPort());
                received = true;
            } catch (SocketTimeoutException ignored) {
            }
        }
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
        System.out.println("ID:\t"+ node.id);
        System.out.println("Name:\t" + node.name);
        System.out.println("IP:\t" + node.ip);
        System.out.println("NamingServer IP:\t" + node.NS_port + ":" + node.NS_port);
    }
}

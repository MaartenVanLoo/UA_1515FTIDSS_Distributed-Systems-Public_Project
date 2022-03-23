package Node;

import java.io.IOException;
import java.net.*;

public class Node {
    private String ip;
    private String name;

    public Node(String name) {
        this.name = name;
    }

    public void discoverNameServer() throws IOException {
        InetAddress broadcastIp = InetAddress.getByName("255.255.255.255");
        String message = new String(name);
        boolean received = false;


        DatagramSocket socket = new DatagramSocket(8000);
        socket.setSoTimeout(1000);
        DatagramPacket discoveryPacket = new DatagramPacket(message.getBytes(), message.length(),
                broadcastIp, 8001);
        byte[] response = new byte[32];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        while(!received) {
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
                received = true;
            }catch (SocketTimeoutException ignored){}
        }
    }


    public static void main(String[] args) throws IOException {
        System.out.println("Starting Node");
        Node node = new Node("THIS NODE");
        node.discoverNameServer();
    }
}

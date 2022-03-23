package Node;

import java.io.IOException;
import java.net.*;

public class Node {
    private String ip;
    private String name;
    private int id;

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

                this.ip = String.valueOf(socket.getInetAddress());
                this.id = Integer.parseInt(responseData);
                received = true;
            }catch (SocketTimeoutException ignored){}
        }
    }


    public static void main(String[] args) throws IOException {
        System.out.println("Starting Node");
        String name;
        if (args.length > 0) {
            name = args[0];
        }else{
            name = "default node";
        }
        Node node = new Node(name);
        node.discoverNameServer();
    }
}

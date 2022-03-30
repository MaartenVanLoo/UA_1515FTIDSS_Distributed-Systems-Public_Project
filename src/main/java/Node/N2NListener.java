package Node;

import NameServer.Hashing;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Thread for listening for incoming multicasts from other Nodes.
 */
public class N2NListener extends Thread {
    private final int LISTENING_PORT = 8002;
    private final int ANSWERING_PORT = 8001;
    private final Node node;
    boolean running = false;
    private DatagramSocket listeningSocket;

    public N2NListener(Node node) {
        this.node = node;
        try {
            this.listeningSocket = new DatagramSocket(LISTENING_PORT);
        } catch (SocketException e) {
            this.listeningSocket = null;
            System.out.println("Node 2 Node Listening disabled");
            e.printStackTrace();
        }
    }

    @Override
    /**
     * Listen for UDP multicasts
     */
    public void run() {
        if (this.listeningSocket == null) return;

        this.running = true;
        while (this.running) {
            try {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                this.listeningSocket.receive(receivePacket);
                String data = new String(receivePacket.getData()).trim();

                String sourceIp = receivePacket.getAddress().getHostAddress();
                String response;

                //Multicast new node:
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(data);
                String type = (String) jsonObject.get("type");
                if (type.equals("Discovery")) {
                    //discovery message
                    String name = (String) jsonObject.get("name");
                    int neighbourId = Hashing.hash(name);

                    if (neighbourId > this.node.getId() && this.node.getNextNodeId() > neighbourId) {
                        //new node is to the right
                        this.node.setNextNodeId(neighbourId);
                        response = "{\"currentId\":\"" + this.node.getId() + "\",\"nextNodeId\":\"" + this.node.getNextNodeId() + "\"}";
                    } else if (neighbourId < this.node.getId() && this.node.getPrevNodeId() < neighbourId) {
                        //new node is to the left
                        this.node.setPrevNodeId(neighbourId);
                        response = "{\"currentId\":\"" + this.node.getId() + "\",\"prevNodeId\":\"" + this.node.getPrevNodeId() + "\"}";
                    } else {
                        response = "";
                    }
                    DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    this.listeningSocket.send(responsePacket);
                } else if (type.equals("Shutdown")) {

                } else {
                    System.out.println("Unknown message type: " + type);
                }
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
    }
}

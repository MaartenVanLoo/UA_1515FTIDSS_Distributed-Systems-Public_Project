package Node;

import Utils.Hashing;
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
    private final int LISTENING_PORT = 8001;
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
        //this.listeningSocket = null; //uncomment this line when runninging on localhost (no 2 sockets listening to the same port)
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
                byte[] receiveData = new byte[1024]; //make new buffer every time!
                DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
                this.listeningSocket.receive(receivedPacket);
                String data = new String(receivedPacket.getData()).trim();
                System.out.println("Received: " + data);
                String sourceIp = receivedPacket.getAddress().getHostAddress();
                String response ="{}";

                //what type is the received packet?
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(data);
                String type = (String) jsonObject.get("type");
                DatagramPacket responsePacket = null;
                if (type.equals("Discovery")) {
                    System.out.println("Received discovery message from " + sourceIp);
                    discoveryHandler(receivedPacket,jsonObject);
                    //print status update
                    this.node.printStatus();
                } else if (type.equals("Shutdown")) {
                    shutdownHandler();
                } else {
                    System.out.println("Unknown message type: " + type);
                }
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private void discoveryHandler(DatagramPacket receivedPacket,JSONObject jsonObject) throws IOException {
        //discovery message
        String name = (String) jsonObject.get("name");
        if (name.equals(this.node.getName())) return; //no answer!
        System.out.println("Name: " + name);
        int neighbourId = Hashing.hash(name);

        if (this.node.getId() == this.node.getPrevNodeId() && this.node.getId() == this.node.getNextNodeId()) {
            //this is the first node in the ring
            updateNextNode(neighbourId, receivedPacket);
            updatePrevNode(neighbourId, receivedPacket);
                        /*
                        this.node.setNextNodeId(neighbourId);
                        this.node.setPrevNodeId(neighbourId);
                        response = "{" +
                                "\"type\":\"NB-next\"," +
                                "\"currentId\":\"" + this.node.getId() + "\"," +
                                "\"nextNodeId\":\"" + this.node.getNextNodeId() + "\"" +
                                "}";
                        responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
                        this.listeningSocket.send(responsePacket);
                        response = "{" +
                                "\"type\":\"NB-prev\"," +
                                "\"currentId\":\"" + this.node.getId() + "\"," +
                                "\"prevNodeId\":\"" + this.node.getPrevNodeId() + "\"" +
                                "}";
                        responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
                        this.listeningSocket.send(responsePacket);
                        */
        }
        else if (this.node.getPrevNodeId() == this.node.getNextNodeId()) {
            //2 nodes in ring
            if (this.node.getNextNodeId() > this.getId()){
                //this node has the lowest ID
                if (neighbourId < this.node.getId()) {
                    updatePrevNode(neighbourId, receivedPacket);
                }else{
                    updateNextNode(neighbourId, receivedPacket);
                }
            }
            else{
                //this node has the highest ID
                if (neighbourId > this.node.getId()) {
                    updateNextNode(neighbourId, receivedPacket);
                }else{
                    updatePrevNode(neighbourId, receivedPacket);
                }
            }
        }
        //TODO: check reasoning for this
        else if (neighbourId > this.node.getId() && this.node.getNextNodeId() < this.node.getId()){
            //This node has the highest id and the next node has the lowest id => now new "highest" node.
            updateNextNode(neighbourId, receivedPacket);
                        /*
                        this.node.setNextNodeId(neighbourId);
                        response = "{" +
                                "\"type\":\"NB-next\"," +
                                "\"currentId\":\"" + this.node.getId() + "\"," +
                                "\"nextNodeId\":\"" + this.node.getNextNodeId() + "\"" +
                                "}";
                        responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
                        this.listeningSocket.send(responsePacket);
                        */
        }
        else if (neighbourId < this.node.getId() && this.node.getPrevNodeId() > this.node.getId()){
            //This node has the lowest id and the next node has the highest id => now new "lowest" node.
            //new node is to the left
            updatePrevNode(neighbourId, receivedPacket);
                        /* this.node.setPrevNodeId(neighbourId);
                        response = "{" +
                                "\"type\":\"NB-prev\"," +
                                "\"currentId\":\"" + this.node.getId() + "\"," +
                                "\"prevNodeId\":\"" + this.node.getPrevNodeId() + "\"" +
                                "}";
                        responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
                        this.listeningSocket.send(responsePacket);

                        */
        }
        else if (neighbourId > this.node.getId() && this.node.getNextNodeId() > neighbourId) {
            //new node is to the right
            updateNextNode(neighbourId, receivedPacket);
                        /*this.node.setNextNodeId(neighbourId);
                        response = "{" +
                                "\"type\":\"NB-next\"," +
                                "\"currentId\":\"" + this.node.getId() + "\"," +
                                "\"nextNodeId\":\"" + this.node.getNextNodeId() + "\"" +
                                "}";
                        responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
                        this.listeningSocket.send(responsePacket);

                        */
        }
        else if (neighbourId < this.node.getId() && this.node.getPrevNodeId() < neighbourId) {
            //new node is to the left
            updatePrevNode(neighbourId, receivedPacket);
                        /*
                        this.node.setPrevNodeId(neighbourId);
                        response = "{" +
                                "\"type\":\"NB-prev\"," +
                                "\"currentId\":\"" + this.node.getId() + "\"," +
                                "\"prevNodeId\":\"" + this.node.getPrevNodeId() + "\"" +
                                "}";
                        responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
                        this.listeningSocket.send(responsePacket);

                         */
        } else {
            System.out.println("Received discovery message from " + receivedPacket.getAddress().getHostAddress() + " but it is not a neighbour");
            //no answer!, never send an empty response!
        }
    }
    private void shutdownHandler(){};
    private void updateNextNode(int neighbourId, DatagramPacket receivedPacket) throws IOException {
        this.node.setNextNodeId(neighbourId);
        String response = "{" +
                "\"type\":\"NB-next\"," +
                "\"currentId\":" + this.node.getId() + "," +
                "\"nextNodeId\":" + this.node.getNextNodeId() + "" +
                "}";
        DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
        this.listeningSocket.send(responsePacket);
    }
    private void updatePrevNode(int neighbourId, DatagramPacket receivedPacket) throws IOException {
        this.node.setPrevNodeId(neighbourId);
        String response = "{" +
                "\"type\":\"NB-prev\"," +
                "\"currentId\":" + this.node.getId() + "," +
                "\"prevNodeId\":" + this.node.getPrevNodeId() + "" +
                "}";
        DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
        this.listeningSocket.send(responsePacket);
    }
}

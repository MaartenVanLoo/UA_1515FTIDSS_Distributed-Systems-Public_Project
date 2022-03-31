package Node;

import Utils.Hashing;
import kong.unirest.Unirest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Thread for listening for incoming multicasts from other Nodes.
 */
public class N2NListener extends Thread {
    private PingNode pingNode;
    private final Node node;
    private boolean running = false;


    public N2NListener(Node node) {
        this.setDaemon(true); //make sure the thread dies when the main thread dies
        this.node = node;
        this.pingNode = new PingNode(node);
        //pingNode.start();
    }

    @Override
    /**
     * Listen for UDP packets
     */
    public void run() {
        if (this.node.getListeningSocket() == null) return;

        this.running = true;
        while (this.running && !this.isInterrupted()) {
            try {
                byte[] receiveData = new byte[1024]; //make new buffer every time!
                DatagramPacket receivedPacket = new DatagramPacket(receiveData, receiveData.length);
                this.node.getListeningSocket().receive(receivedPacket);

                if (!this.node.isSetUp()) continue; //Do not answer packets till the node is set up

                String data = new String(receivedPacket.getData()).trim();
                System.out.println("Received: " + data);
                String sourceIp = receivedPacket.getAddress().getHostAddress();
                String response ="{}";

                //what type is the received packet?
                JSONParser parser = new JSONParser();
                JSONObject jsonObject = (JSONObject) parser.parse(data);
                String type = (String) jsonObject.get("type");

                switch (type) {
                    case "Discovery":
                        System.out.println("Received discovery message from " + sourceIp);
                        discoveryHandler(receivedPacket, jsonObject);
                        break;
                    case "Shutdown":
                        System.out.println("Received shutdown message from " + sourceIp);
                        shutdownHandler(receivedPacket, jsonObject);
                        this.node.printStatus();
                        this.node.validateNode();
                        break;
                    case "Failure":
                        System.out.println("Received failure message from " + sourceIp);
                        failureHandler(receivedPacket, jsonObject);
                        this.node.printStatus();
                        this.node.validateNode();
                        break;
                    case "Ping":
                        System.out.println("Received ping message from " + sourceIp);
                        pingHandler(receivedPacket);
                        break;
                    case "PingReply":
                        System.out.println("Received ping replay message from " + sourceIp);
                        pingReplayHandler(jsonObject);
                        break;
                    default:
                        System.out.println("Unknown message type: " + type);
                        break;
                }
            }
            catch (SocketException ignored){} //thrown when the socket is closed
            catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown(){
        this.running = false;
    }
    /**
     * Handles UDP packets received from other nodes when they enter the network.
     * @param receivedPacket
     * @param jsonObject
     * @throws IOException
     */
    private void discoveryHandler(DatagramPacket receivedPacket,JSONObject jsonObject) throws IOException {
        //TODO: still error when the lowest node gets a new "highest" node neighbor. The lowest node doesn't detect that the new node is actually a new neighbor ands sends a "not a neighbor" message.
        // Easy solution => ask nameserver what my neighbors are
        // Hard solution => work out the logic by hand....?


        //discovery message
        String name = (String) jsonObject.get("name");
        if (name.equals(this.node.getName())) return; //no answer!
        System.out.println("Name: " + name);
        int neighbourId = Hashing.hash(name);

        // if the old prevId and NextId are equal to the currentId, then the new node is the second node in the
        // network, and the prevId and nextId should be updated to the new node's Id
        if (this.node.getId() == this.node.getPrevNodeId() && this.node.getId() == this.node.getNextNodeId()) {
            //this is the first node in the ring
            updateNextNode(neighbourId, receivedPacket);
            updatePrevNode(neighbourId, receivedPacket);
        }
        // if both the old prevId and nextId are the same, then there were 2 nodes and the new node is the 3rd
        else if (this.node.getPrevNodeId() == this.node.getNextNodeId()) {
            //2 nodes in ring
            if (this.node.getNextNodeId() > this.node.getId()){
                //this node has the lowest ID
                if (neighbourId > this.node.getId() && neighbourId < this.node.getNextNodeId()) {
                    //Node in between
                    updateNextNode(neighbourId, receivedPacket);
                }else{
                    updatePrevNode(neighbourId, receivedPacket);
                }
            }
            else{
                //this node has the highest ID
                if (neighbourId < this.node.getId() && neighbourId > this.node.getNextNodeId()) {
                    //Node in between
                    updatePrevNode(neighbourId, receivedPacket);
                }else{
                    updateNextNode(neighbourId, receivedPacket);
                }
            }
        }
        // nextID < currentID < neigbourID
        else if (neighbourId > this.node.getId() && this.node.getNextNodeId() < this.node.getId()){
            //This node has the highest id and the next node has the lowest id => now new "highest" node.
            updateNextNode(neighbourId, receivedPacket);
        }
        else if (neighbourId <this.node.getNextNodeId() && this.node.getNextNodeId() < this.node.getId()) {
            //This node has the highest id and the next node has the lowest id => update next node
            updateNextNode(neighbourId, receivedPacket);
        }
        // neighbourID < currentID < prevID
        else if (neighbourId < this.node.getId() && this.node.getPrevNodeId() > this.node.getId()){
            //This node has the lowest id and the next node has the highest id => now new "lowest" node.
            //new node is to the left
            updatePrevNode(neighbourId, receivedPacket);
        }
        else if (neighbourId > this.node.getPrevNodeId() && this.node.getPrevNodeId() > this.node.getId()){
            //This node has the lowest id and the next node has the highest id =>update prev node
            updatePrevNode(neighbourId, receivedPacket);
        }
        // currentID < neighbourID < nextID
        else if (neighbourId > this.node.getId() && this.node.getNextNodeId() > neighbourId) {
            //new node is to the right
            updateNextNode(neighbourId, receivedPacket);
        }
        // prevID < neighbourID < currentID
        else if (neighbourId < this.node.getId() && this.node.getPrevNodeId() < neighbourId) {
            //new node is to the left
            updatePrevNode(neighbourId, receivedPacket);
        } else {
            System.out.println("Received discovery message from " + receivedPacket.getAddress().getHostAddress() + " but it is not a neighbour");
            //no answer!, never send an empty response!
        }
        this.node.printStatus();
        this.node.validateNode();
    }
    private void shutdownHandler(DatagramPacket receivedPacket,JSONObject jsonObject){
        if (jsonObject.containsKey("nextNodeId")) {
            this.node.setNextNodeId((long)jsonObject.get("nextNodeId"));
            //this.node.setNextNodeIP(Unirest.get("http://"+this.node.getNS_ip()+":8081/ns/getNextIP?currentID="+this.node.getId()).asString().getBody());
            //note: you can't trust that the nameserver already updated the shutdown of the node. => ask for ip with ID
            this.node.setNextNodeIP(Unirest.get("/ns/getNodeIP").queryString("id",this.node.getNextNodeId()).asString().getBody());

        }
        if (jsonObject.containsKey("prevNodeId")) {
            this.node.setPrevNodeId((long)jsonObject.get("prevNodeId"));
            //this.node.setPrevNodeIP(Unirest.get("http://"+this.node.getNS_ip()+":8081/ns/getPrevIP?currentID="+this.node.getId()).asString().getBody());
            //note: you can't trust that the nameserver already updated the shutdown of the node. => ask for ip with ID
            this.node.setPrevNodeIP(Unirest.get("/ns/getNodeIP").queryString("id",this.node.getPrevNodeIP()).asString().getBody());
        }
    }
    private void failureHandler(DatagramPacket receivedPacket,JSONObject jsonObject){
        if (jsonObject.containsKey("nextNodeId") && jsonObject.get("failed").equals(this.node.getNextNodeId())) {
            this.node.setNextNodeId((long) jsonObject.get("nextNodeId"));
            this.node.setNextNodeIP(jsonObject.get("nextNodeIP").toString());
        }
        if (jsonObject.containsKey("prevNodeId") && jsonObject.get("failed").equals(this.node.getPrevNodeId())) {
            this.node.setPrevNodeId((long)jsonObject.get("prevNodeId"));
            this.node.setPrevNodeIP(jsonObject.get("prevNodeIP").toString());
        }
    }
    private void pingHandler(DatagramPacket receivedPacket){
        //TODO: check if ping is from neighbour, otherwise a neighbour must have failed?
        //echo back a ping replay
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", "PingReply");
        jsonObject.put("nodeId", this.node.getId());
        DatagramPacket datagramPacket = new DatagramPacket(jsonObject.toString().getBytes(), jsonObject.toString().getBytes().length, receivedPacket.getAddress(), receivedPacket.getPort());
        try {
            this.node.getListeningSocket().send(datagramPacket);
        } catch (IOException ignored) {}
    }
    private void pingReplayHandler(JSONObject jsonObject){
        long id = (long)jsonObject.get("nodeId");
        if (id == this.node.getNextNodeId()){
            this.pingNode.resetNext();
        }
        if (id == this.node.getPrevNodeId()){
            this.pingNode.resetPrev();
        }
    }
    private void updateNextNode(int neighbourId, DatagramPacket receivedPacket) throws IOException {
        this.node.setNextNodeId(neighbourId);
        String response = "{" +
                "\"type\":\"NB-next\"," +
                "\"currentId\":" + this.node.getId() + "," +
                "\"nextNodeId\":" + this.node.getNextNodeId() + "" +
                "}";
        DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
        this.node.getListeningSocket().send(responsePacket);
        //TODO:: Can you trust that the nameserver already updated the shutdown of the node?
        this.node.setNextNodeIP(Unirest.get("/ns/getNextIP?currentID="+this.node.getId()).asString().getBody());
    }
    private void updatePrevNode(int neighbourId, DatagramPacket receivedPacket) throws IOException {
        this.node.setPrevNodeId(neighbourId);
        String response = "{" +
                "\"type\":\"NB-prev\"," +
                "\"currentId\":" + this.node.getId() + "," +
                "\"prevNodeId\":" + this.node.getPrevNodeId() + "" +
                "}";
        DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
        this.node.getListeningSocket().send(responsePacket);
        //TODO:: Can you trust that the nameserver already updated the shutdown of the node?
        this.node.setPrevNodeIP(Unirest.get("/ns/getPrevIP?currentID="+this.node.getId()).asString().getBody());
    }


}

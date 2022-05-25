package Node;

import Utils.Hashing;
import kong.unirest.Unirest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Thread for listening for incoming multicasts from other Nodes.
 */
public class N2NListener extends Thread {
    private PingNode pingNode;
    private final Node node;
    private volatile boolean running = false;
    Thread fileUpdateThread = null;


    public N2NListener(Node node) {
        this.setDaemon(true); //make sure the thread dies when the main thread dies
        this.node = node;
        this.start();
        this.pingNode = new PingNode(node);
        pingNode.start();

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
                //System.out.println("Received: " + data);
                String sourceIp = receivedPacket.getAddress().getHostAddress();

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
                        //System.out.println("Received ping message from " + sourceIp);
                        pingHandler(receivedPacket);
                        break;
                    case "PingAck":
                        //System.out.println("Received ping ack message from " + sourceIp);
                        pingAckHandler(receivedPacket,jsonObject);
                        break;
                    case "PingNack":
                        System.out.println("Received ping nack message from " + sourceIp);
                        pingNackHandler(receivedPacket,jsonObject);
                        break;
                    default:
                        System.out.println("Unknown message type: " + type);
                        break;
                }
            }
            catch (SocketException e){
                if (this.node.getListeningSocket() == null || this.node.getListeningSocket().isClosed()) {
                    this.running = false;
                }else{
                    e.printStackTrace();
                }
            } //thrown when the socket is closed
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
        //System.out.println("Name: " + name);
        int neighbourId = Hashing.hash(name);
        //System.out.println("NeighbourId: " + neighbourId);

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
            //System.out.println("Received discovery message from " + receivedPacket.getAddress().getHostAddress() + " but it is not a neighbour");
            //no answer!, never send an empty response!
        }
        //this.node.printStatus();
        //this.node.validateNode();

        //check if files needs to be updated in a new thread
        if (!(fileUpdateThread == null)){
            try {
                fileUpdateThread.join();
            }
            catch(Exception e){
                e.printStackTrace();
                System.out.println("no files updated");
                return;
            }
        }

        fileUpdateThread = new Thread(createRunnableUpdateFiles(this.node,neighbourId));
        fileUpdateThread.start();
    }
    private void shutdownHandler(DatagramPacket receivedPacket,JSONObject jsonObject){
        if (this.node.getNextNodeId() == this.node .getId() && this.node.getPrevNodeId() == this.node.getId()){
            //this is the only node in the ring and receiving its own shutdown message
            return;
        }
        if (jsonObject.containsKey("nextNodeId")) {
            this.node.setNextNodeId((long)jsonObject.get("nextNodeId"));
            this.node.setNextNodeIP((String)jsonObject.get("nextNodeIP"));
            //this.node.setNextNodeIP(Unirest.get("http://"+this.node.getNS_ip()+":8081/ns/getNextIP?currentID="+this.node.getId()).asString().getBody());
            //note: you can't trust that the nameserver already updated the shutdown of the node. => ask for ip with ID
            //this.node.setNextNodeIP(Unirest.get("/ns/getNodeIP").queryString("id",this.node.getNextNodeId()).asString().getBody());
        }
        if (jsonObject.containsKey("prevNodeId")) {
            this.node.setPrevNodeId((long)jsonObject.get("prevNodeId"));
            this.node.setPrevNodeIP((String)jsonObject.get("prevNodeIP"));
            //this.node.setPrevNodeIP(Unirest.get("http://"+this.node.getNS_ip()+":8081/ns/getPrevIP?currentID="+this.node.getId()).asString().getBody());
            //note: you can't trust that the nameserver already updated the shutdown of the node. => ask for ip with ID
            //this.node.setPrevNodeIP(Unirest.get("/ns/getNodeIP").queryString("id",this.node.getPrevNodeId()).asString().getBody());
        }
    }
    private void failureHandler(DatagramPacket receivedPacket,JSONObject jsonObject){

        if (!jsonObject.containsKey("nodeId")){
            System.out.println("Bad formatted failure message");
        }
        long nodeId = (long)jsonObject.get("nodeId");
        //check if nodeId = neighbourId
        if (nodeId == this.node.getNextNodeId() || nodeId == this.node.getPrevNodeId()){
            //failed node is neighbour
            //request correct configuration from nameserver
            try {
                String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(this.node.getId())).asString().getBody();
                JSONObject json = (JSONObject) this.node.getParser().parse(response);
                JSONObject prevNode = (JSONObject) json.get("prev");
                JSONObject nextNode = (JSONObject) json.get("next");
                this.node.setPrevNodeId((long)prevNode.get("id")); System.out.println("update prevNodeId: "+this.node.getPrevNodeId());
                this.node.setPrevNodeIP((String)prevNode.get("ip")); System.out.println("update prevNodeIP: "+this.node.getPrevNodeIP());
                this.node.setNextNodeId((long)nextNode.get("id")); System.out.println("update nextNodeId: "+this.node.getNextNodeId());
                this.node.setNextNodeIP((String)nextNode.get("ip")); System.out.println("update nextNodeIP: "+this.node.getNextNodeIP());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }else{
            System.out.println("NodeID " + nodeId + " is not a neighbour");
        }
        //reset counter to avoid cascading failures
        this.pingNode.resetPrev();
        this.pingNode.resetNext();
    }
    private void pingHandler(DatagramPacket receivedPacket){
        //TODO: check if ping is from neighbour, otherwise a neighbour must have failed?
        JSONObject jsonObject = new JSONObject();
        //check if source is from neighbour

        if (receivedPacket.getAddress().getHostAddress().equals(this.node.getNextNodeIP()) || receivedPacket.getAddress().getHostAddress().equals(this.node.getPrevNodeIP())){
            //ping is from neighbour
            jsonObject.put("type", "PingAck");
            jsonObject.put("nodeId", this.node.getId());
        }else{
            //ping is from another node
            jsonObject.put("type", "PingNack");
            jsonObject.put("nodeId", this.node.getId());

            //Am I the node with a misconfiguration? => request correct configuration from nameserver
            try {
                String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(this.node.getId())).asString().getBody();
                JSONObject json = (JSONObject) this.node.getParser().parse(response);
                JSONObject prevNode = (JSONObject) json.get("prev");
                JSONObject nextNode = (JSONObject) json.get("next");
                this.node.setPrevNodeId((long)prevNode.get("id")); System.out.println("update prevNodeId: "+this.node.getPrevNodeId());
                this.node.setPrevNodeIP((String)prevNode.get("ip")); System.out.println("update prevNodeIP: "+this.node.getPrevNodeIP());
                this.node.setNextNodeId((long)nextNode.get("id")); System.out.println("update nextNodeId: "+this.node.getNextNodeId());
                this.node.setNextNodeIP((String)nextNode.get("ip")); System.out.println("update nextNodeIP: "+this.node.getNextNodeIP());
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        //echo back a ping replay
        DatagramPacket datagramPacket = new DatagramPacket(jsonObject.toString().getBytes(), jsonObject.toString().getBytes().length, receivedPacket.getAddress(), receivedPacket.getPort());
        try {
            this.node.getListeningSocket().send(datagramPacket);
        } catch (IOException ignored) {}
    }
    private void pingAckHandler(DatagramPacket receivedPacket, JSONObject jsonObject){
        long id = (long)jsonObject.get("nodeId");
        if (id == this.node.getNextNodeId()){
            this.pingNode.resetNext();
        }
        if (id == this.node.getPrevNodeId()){
            this.pingNode.resetPrev();
        }
    }
    private void pingNackHandler(DatagramPacket receivedPacket, JSONObject jsonObject){
        long id = (long)jsonObject.get("nodeId");
        if (id == this.node.getNextNodeId()){
            this.pingNode.resetNext();
        }
        if (id == this.node.getPrevNodeId()){
            this.pingNode.resetPrev();
        }
        //some config is wrong, request correct config from nameserver
        try {
            String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(this.node.getId())).asString().getBody();
            System.out.println(response);
            JSONObject json = (JSONObject) this.node.getParser().parse(response);
            JSONObject prevNode = (JSONObject) json.get("prev");
            JSONObject nextNode = (JSONObject) json.get("next");
            this.node.setPrevNodeId((long)prevNode.get("id"));
            this.node.setPrevNodeIP((String)prevNode.get("ip"));
            this.node.setNextNodeId((long)nextNode.get("id"));
            this.node.setNextNodeIP((String)nextNode.get("ip"));
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void updateNextNode(int neighbourId, DatagramPacket receivedPacket) throws IOException {
        this.node.setNextNodeId(neighbourId);
        this.node.setNextNodeIP(receivedPacket.getAddress().getHostAddress());
        String response = "{" +
                "\"type\":\"NB-next\"," +
                "\"currentId\":" + this.node.getId() + "," +
                "\"nextNodeId\":" + this.node.getNextNodeId() + "" +
                "}";
        DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
        this.node.getListeningSocket().send(responsePacket);
        return;
        //TODO:: Can you trust that the nameserver already updated the shutdown of the node? => no??? origin of problems during lecture 20/4 ?
        //Get config from nameserver
        /*JSONParser parser = new JSONParser();
        JSONObject config = new JSONObject();
        try {
            config = (JSONObject) parser.parse(Unirest.get("/ns/nodes/{Id}").routeParam("Id",String.valueOf(this.node.getId())).asString().getBody());
        } catch (ParseException ignore) {}
        JSONObject next = (JSONObject) config.get("next");
        this.node.setNextNodeIP(next.get("ip").toString());*/
    }
    private void updatePrevNode(int neighbourId, DatagramPacket receivedPacket) throws IOException {
        this.node.setPrevNodeId(neighbourId);
        this.node.setPrevNodeIP(receivedPacket.getAddress().getHostAddress());
        String response = "{" +
                "\"type\":\"NB-prev\"," +
                "\"currentId\":" + this.node.getId() + "," +
                "\"prevNodeId\":" + this.node.getPrevNodeId() + "" +
                "}";
        DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivedPacket.getAddress(), receivedPacket.getPort());
        this.node.getListeningSocket().send(responsePacket);
        return;
        //TODO:: Can you trust that the nameserver already updated the shutdown of the node?
        /*
        //Get config from nameserver
        JSONParser parser = new JSONParser();
        JSONObject config = new JSONObject();
        try {
            config = (JSONObject) parser.parse(Unirest.get("/ns/nodes/{Id}").routeParam("Id",String.valueOf(this.node.getId())).asString().getBody());
        } catch (ParseException ignore) {}
        JSONObject prev = (JSONObject) config.get("prev");
        this.node.setPrevNodeIP(prev.get("ip").toString());
        */

    }


    private Runnable createRunnableUpdateFiles(Node node, int new_id){
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                //note: other case in which the next node is updated is handled by the nameserver notifying the previous node
                System.out.println("update fileLocationOtherNewNode " + new_id);
                node.getFileManager().updateFileLocationOtherNewNode(new_id);
            }
        };
        return runnable;
    }
}

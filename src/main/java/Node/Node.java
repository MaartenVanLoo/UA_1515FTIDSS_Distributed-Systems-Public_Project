package Node;

import Utils.SynchronizedPrint;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import kong.unirest.Unirest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;

import java.net.*;
import java.nio.file.AccessDeniedException;
import java.util.Objects;
import java.util.concurrent.*;

import static java.lang.System.exit;

public class Node {
    //<editor-fold desc="global variables">
    private static final int LISTENING_PORT = 8001;

    private String ip;          // ip address of the node
    private final String name;  // name of the node
    private int id;             // id/hash of the node
    private String NS_ip;       // ip addr of the namingserver
    private String NS_port;     // port of the namingserver
    private long nodeCount;     // # nodes in the network  
    private long prevNodeId;    // id of the previous node in the network
    private String prevNodeIP;  // ip addr of the previous node in the network
    private long nextNodeId;    // id of the next node in the network
    private String nextNodeIP;  // ip addr of the next node in the network


    private final JSONParser parser = new JSONParser();
    private final N2NListener n2NListener;
    private final NodeAPI nodeAPI;
    private DatagramSocket listeningSocket;

    private FileTransfer fileTransfer;

    private boolean setUpComplete = false;
    //</editor-fold>

    public Node(String name) throws IOException {
        // turn logger off so it doesn't clutter the console
        Logger root = (Logger) org.slf4j.LoggerFactory.getLogger("org.apache.http");
        root.setLevel(Level.OFF);
        this.name = name;

        try {
            this.listeningSocket= new DatagramSocket(LISTENING_PORT);
        } catch (SocketException e) {
            this.listeningSocket = null;
            System.out.println("Node 2 Node Listening disabled");
            e.printStackTrace();
        }

        this.n2NListener = new N2NListener(this);
        this.n2NListener.start();
        this.nodeAPI = new NodeAPI(this);
        this.nodeAPI.start();
        this.fileTransfer = new FileTransfer(LISTENING_PORT);
    }



    // Send broadcasts until the NS answers
    public void discoverNameServer() throws IOException {
        InetAddress broadcastIp = InetAddress.getByName("255.255.255.255");
        String message = "{\"type\":\"Discovery\",\"name\":\"" + name + "\"}";
        boolean received = false;
        boolean resend = false;


        DatagramSocket socket = new DatagramSocket(8000);
        socket.setSoTimeout(1000);
        DatagramPacket discoveryPacket = new DatagramPacket(message.getBytes(), message.length(),
                broadcastIp, 8001);
        byte[] response = new byte[256];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);

        while (!received) {
            // Discovery request command
            if (resend) socket.send(discoveryPacket);
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

                //parse response data:
                JSONParser parser = new JSONParser();
                JSONObject obj = (JSONObject)parser.parse(responseData);
                String type = obj.get("type").toString();
                if (type.equals("NS-offer")) {
                    String status = obj.get("status").toString();
                    if (status.equals("OK")) {
                        JSONObject node = (JSONObject) obj.get("node");
                        this.id = (int)   (long) ((JSONObject)node.get("node")).get("id");
                        this.prevNodeId = (long)   ((JSONObject)node.get("prev")).get("id");
                        this.prevNodeIP = (String) ((JSONObject)node.get("prev")).get("ip");
                        this.nextNodeId = (long)   ((JSONObject)node.get("next")).get("id");
                        this.nextNodeIP = (String) ((JSONObject)node.get("next")).get("ip");
                        this.nodeCount  = (long)   obj.get("nodeCount");
                    } else if (status.equals("Access Denied")) {
                        throw new AccessDeniedException("Access to network denied by nameserver");
                    }

                    this.ip = InetAddress.getLocalHost().toString().split("/")[1].split(":")[0];
                    this.NS_ip = String.valueOf(responsePacket.getAddress().getHostAddress());
                    this.NS_port = String.valueOf(responsePacket.getPort());
                    received = true; //no need to read the neighbour's messages when all information already obtained from the NS
                }
                else if (type.equals("NB-next")) {
                    this.nextNodeId = (long) (((JSONObject) obj).get("currentId"));
                    resend = false;
                }else if (type.equals("NB-prev")) {
                    this.prevNodeId = (long) (((JSONObject) obj).get("currentId"));
                    resend = false;
                }else{
                    System.out.println("Unknown response type");
                    resend =true;
                }
            } catch (SocketTimeoutException ignored) {
                resend = true;
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        Unirest.config().defaultBaseUrl("http://"+this.NS_ip +":8081");
        this.setUpComplete = true;
    }

    // ask the naming server for the location of a file
    public void getFileLocation(String filename) {
        try {
            //String url = "http://" + this.NS_ip + ":8081/ns/getFile?fileName="+filename;
            System.out.println(Unirest.get("/ns/files/{fileName}").routeParam("fileName", filename)
                    .asString().getBody());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // exit the network
    public void shutdown(){
        try {
            System.out.println("Shutting down...");
            this.setUpComplete = false;
            String updatePrev;
            String updateNext;

            // first send shutdown to nameserver and then neighbours
            // update namingserver
            System.out.println(Unirest.delete("/ns/nodes/{nodeID}").routeParam("nodeID", String.valueOf(this.id)).asString().getBody());


            // update prev node
            updatePrev = "{\"type\":\"Shutdown\"," +
                           "\"nextNodeId\":"+this.getNextNodeId() + "," +
                           "\"nextNodeIP\":\""+this.getNextNodeIP() +"\"}";

            DatagramPacket prevNodePacket = new DatagramPacket(updatePrev.getBytes(), updatePrev.length(),
                    InetAddress.getByName(prevNodeIP), 8001);

            DatagramSocket socket = new DatagramSocket();
            socket.send(prevNodePacket);

            // update next node
            updateNext = "{\"type\":\"Shutdown\"," +
                          "\"prevNodeId\":"+this.getPrevNodeId() + "," +
                          "\"prevNodeIP\":\""+this.getPrevNodeIP() +"\"}";
            DatagramPacket nextNodePacket = new DatagramPacket(updateNext.getBytes(), updateNext.length(),
                    InetAddress.getByName(nextNodeIP), 8001);
            //send this.nextNodeID to prevNodeID
            socket.send(nextNodePacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.listeningSocket.close(); //close the listening socket, this will cause the N2N to exit
        this.nodeAPI.stop();
        System.out.println("Shutdown complete");
    }

    // print the variables of the node to the console
    public void printStatus(){
        synchronized (SynchronizedPrint.lock) {
            System.out.println("Node name:   \t" + this.name);
            System.out.println("Node ip:     \t" + this.ip);
            System.out.println("Node id:     \t" + this.id);
            System.out.println("Node ns ip:  \t" + this.NS_ip);
            System.out.println("Node ns port:\t" + this.NS_port);
            System.out.println("Node prev id:\t" + this.prevNodeId);
            System.out.println("Node prev ip:\t" + this.prevNodeIP);
            System.out.println("Node next id:\t" + this.nextNodeId);
            System.out.println("Node next ip:\t" + this.nextNodeIP);
            System.out.println("Node nodeCount:\t" + this.nodeCount);
        }
    }

    public long getPrevNodeId() {
        return prevNodeId;
    }

    public void setPrevNodeId(long prevNodeId) {
        this.prevNodeId = prevNodeId;
    }

    public long getNextNodeId() {
        return nextNodeId;
    }

    public void setNextNodeId(long nextNodeId) {
        this.nextNodeId = nextNodeId;
    }

    public String getPrevNodeIP() {
        return prevNodeIP;
    }

    public String getNS_ip() {
        return NS_ip;
    }

    public String getNS_port() {
        return NS_port;
    }

    public DatagramSocket getListeningSocket() {
        return listeningSocket;
    }

    public void setListeningSocket(DatagramSocket listeningSocket) {
        this.listeningSocket = listeningSocket;
    }

    public void setPrevNodeIP(String prevNodeIP) {
        this.prevNodeIP = prevNodeIP;
    }

    public String getNextNodeIP() {
        return nextNodeIP;
    }

    public void setNextNodeIP(String nextNodeIP) {
        this.nextNodeIP = nextNodeIP;
    }

    public int getId() {
        return id;
    }

    public String getIP() {
        return ip;
    }

    public String getName() {
        return name;
    }

    /**
     * This algorithm is activated in every exception thrown during communication with other nodes.
     * This allows distributed detection of node failure.
     * @param targetId
     * integer of targetId
     *
     */
    public void failureHandler(int targetId){
        // update namingserver
        System.out.println(Unirest.delete("/ns/nodes/{nodeID}/fail").routeParam("nodeID", String.valueOf(targetId)).asString().getBody());
        System.out.println("Node " + targetId + " has failed");
        // TODO: request the prev node and next node params from the NS

        // TODO: update the 'next node' param of the prev node with the info received from the NS

        // TODO: Update the 'previous node' parameter of the next node with the information received from the nameserver

        // TODO: Remove the node from the Naming server
    }

    /**
     * Helper function to validate the current node's parameters by asking the nameserver
     */
    public void validateNode(){
        boolean flag = false;
        try {
            String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(this.id)).asString().getBody();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(response);
            JSONObject prevNode = (JSONObject) json.get("prev");
            JSONObject nextNode = (JSONObject) json.get("next");
            if (this.prevNodeId != (long)  prevNode.get("id")) {System.out.println("prevNodeId is not valid\n\texpected:" + (long)  prevNode.get("id")); flag = true;}
            if (!Objects.equals(this.prevNodeIP, prevNode.get("ip"))) {System.out.println("prevNodeIP is not valid\n\texpected:" + nextNode.get("ip")); flag = true;}
            if (this.nextNodeId != (long)  nextNode.get("id")) {System.out.println("nextNodeId is not valid\n\texpected:" + (long)  nextNode.get("id")); flag = true;}
            if (!Objects.equals(this.nextNodeIP, nextNode.get("ip"))) {System.out.println("nextNodeIP is not valid\n\texpected:" + nextNode.get("ip")); flag = true;}
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (flag) {
            System.out.println("Node " + this.id + " is not valid");
        }else{
            System.out.println("Node " + this.id + " is OK");
        }
    }
    public boolean isSetUp() {
        return this.setUpComplete;
    }
    public JSONParser getParser(){
        return this.parser;
    }

    public void startupFilesCheck(){

    }


    public static void launchNode(String name) throws IOException, InterruptedException{
        Node node = new Node(name);
        try {
            node.discoverNameServer();
        } catch (AccessDeniedException e) {
            exit(-1);
        }
        node.printStatus();
        node.validateNode();

        InetAddress ip = InetAddress.getLocalHost();
        String hostname = ip.getHostName();
        System.out.println("Your current IP address : " + ip);
        System.out.println("Your current Hostname : " + hostname);

        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2);
        Runnable validator = () -> {
            SynchronizedPrint.clearConsole();
            node.printStatus();
            node.validateNode();
        };
        executorService.scheduleAtFixedRate(validator, 1, 5, TimeUnit.SECONDS);

        Thread.sleep(6000000 + 2 * (long) ((Math.random() - 0.5) * 30000)); // sleep for 60±30 seconds
        executorService.shutdownNow();
        node.shutdown();
    }
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Starting Node");
        String name;
        // if the user didn't specify the name of the node, set the name to "default node"
        if (args.length > 0) {
            name = args[0];
        } else {
            name = "default node";
        }

        System.out.println("Network interfaces:");
        System.out.println(NetworkInterface.getNetworkInterfaces());
        while (true) {
            launchNode(name);
            System.gc();
            Thread.sleep((long) (Math.random() * 10000)); // sleep for a value between 0-10 seconds
        }
    }


}

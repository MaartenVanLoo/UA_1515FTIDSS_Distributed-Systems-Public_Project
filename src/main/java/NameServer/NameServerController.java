package NameServer;

import Utils.Hashing;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import NameServer.NameServerStatusCodes.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

//TODO: clean API:
// Proposal:
// All node related information under "/ns/nodes"
// To interact with a node : "/ns/nodes/{nodeId}", the type of interaction is specified in the request type. Additional information is specified in the body of the request.
// To interact with a node based on names : "/ns/nodes/name/{name}"; usually this won't be used but it can be usefully to have.
// To signal failure of a node : "/ns/nodes/{nodeId}/fail"
// Suggestion: reverse node lookup? => ip in , id out: /ns/nodes/ip/{ip}?? => for now not used.
// All file related information under "/ns/files"
// To interact with a specific file: "/ns/files/{fileName}", the type of interaction is specified in the request type.
// Examples:
// GET /ns/nodes/8812 => get all information about node with id 8812, this is a full JSON object containing current id and ip and for all it's neighbors.
// PUT /ns/nodes/8812 => update the information about node with id 8812, this is a full JSON object containing current id and ip and for all it's neighbors.
// POST /ns/nodes => NOTE: this is impossible as new nodes are discovered automatically.
// DELETE /ns/nodes/8812 => delete the information about node with id 8812.
// GET /ns/files/file1.txt => Json string with location of file1.txt
// future:
// GET /ns/nodes => get a list of all available nodes and there configuration in JSON, use "?" to set constraints, filter, the list

/**
 * Class for handling REST API requests.<br>
 * Overview:<br>
 * /ns_________________________returns a JSON with the status of the name server.<br>
 * /ns/nodes___________________returns a JSON with the nodes currently registered.<br>
 * /ns/nodes/{nodeId}__________returns a JSON of the node with the given id.<br>
 * /ns/nodes/{nodeId}/fail_____deletes the node that failed and notifies the other nodes.<br>
 * /ns/files_____________________returns a JSON with all the files in the system (NOT IMPLEMENTED)<br>
 * /ns/files/{fileName}________returns the IP of the node that has the file with the given name.<br>
 * /ns/files/{fileName}/id_____returns the ID of the node that has the file with the given name.
 */
@CrossOrigin(origins = "*") //Used to allow access from any origin
@RestController
public class NameServerController {

    /**
     * Logger for this class
     */
    Logger logger = LoggerFactory.getLogger(NameServerController.class);

    /**
     * Port to send UDP packets from.
     */
    static final int DATAGRAM_PORT = 8001;

    /**
     * NameServer object which contains the logic behind the NameServer application
     */
    private NameServer nameServer;

    /**
     * Parser for all the JSON messages.
     */
    private JSONParser jsonParser = new JSONParser();

    /**
     * Object for handling the UDP Discovery packets.
     */
    DiscoveryHandler discoveryHandler;

    /**
     * DatagramSocket for sending UDP packets.
     */
    private DatagramSocket socket;

    /**
     * Constructor for the NameServerController.<br>
     * Initializes the NameServer object and the DiscoveryHandler object, and starts the DiscoveryHandler thread.
     */
    public NameServerController() {
        this.nameServer = new NameServer();
        try {
            this.socket = new DatagramSocket(DATAGRAM_PORT);
        } catch (SocketException e) {
            logger.warn("Automatic discovery disabled, unable to create datagram socket");
            this.socket = null;
        }
        this.discoveryHandler = new DiscoveryHandler(this);
        discoveryHandler.start();
    }

    /**
     * Returns the NameServer object.
     *
     * @return NameServer object
     */
    public NameServer getNameServer() {
        return this.nameServer;
    }

    /**
     * Method for handling the GET request for the /ns endpoint.<br>
     * Returns a JSON with the status of the NameServer in a JSON format.
     *
     * @return JSON with the status of the NameServer.
     */
    @ResponseStatus(HttpStatus.OK) //200
    @GetMapping(value = "/ns", produces = "application/json")
    public String getNameServerStatus() {
        this.nameServer.getIpMapLock().readLock().lock();
        String response =
                "{\"Status\": \"running\"," +
                        "\"Utilities\":{" +
                        "\"Discovery\":\"" + (this.socket == null ? "disabled" : "enabled") + "\"" +
                        "}," +
                        "\"Nodes\":" + this.nameServer.getIpMapping().size() + "," +
                        "\"Mapping\":[" +
                        this.nameServer.getIpMapping().keySet().stream().map(s -> "{" + this.nameServer.nodeToString(s) + "}").collect(Collectors.joining(",")) +
                        "]}";
        this.nameServer.getIpMapLock().readLock().unlock();
        return response;
    }

    /**
     * Method for handling the GET request for the /ns/nodes endpoint.<br>
     * Returns a String with the nodes currently registered in the NameServer.
     *
     * @return String with the nodes currently registered.
     */
    @ResponseStatus(HttpStatus.OK) // 200
    @GetMapping("/ns/nodes")
    public String getAllNodes() {
        this.nameServer.getIpMapLock().readLock().lock();
        String response = "{" + this.nameServer.getIpMapping().entrySet().stream().map(e -> e.getKey() + " => " + e.getValue()).collect(Collectors.joining("\n")) + "}";
        this.nameServer.getIpMapLock().readLock().unlock();
        return response;
    }

    /**
     * Method for handling the GET request for the /ns/nodes/{nodeId} endpoint.<br>
     * Returns a String of the node with the given id in a JSON format.
     *
     * @param nodeId id of the node to get
     * @return JSON formatted String of the node.
     */
    @ResponseStatus(HttpStatus.OK) // 200
    @GetMapping(value = "/ns/nodes/{nodeId}", produces = "application/json")
    public String getNode(@PathVariable int nodeId) {
        return this.nameServer.nodeToJson(nodeId); //no need for lock as this is only 1 operation (every method is thread safe)
    }

    /**
     * Method for handling the PUT request for the /ns/nodes/{nodeId} endpoint.<br>
     * Updates the IP address of the node with the given id.
     *
     * @param nodeId   id of the node to update
     * @param nodeJson JSON formatted String of the node to update
     */
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204
    @PutMapping("/ns/nodes/{nodeId}")
    public void putNode(@PathVariable int nodeId, @RequestBody String nodeJson) {
        String ip;
        try {
            // parse the JSON string to get the IP address
            JSONObject node = (JSONObject) this.jsonParser.parse(nodeJson);
            ip = node.get("ip") != null ? (String) node.get("ip") : null;
            if (ip == null)
                throw new JSONInvalidFormatException("No ip specified"); //return "HttpStatus.BAD_REQUEST" 400
        } catch (ParseException e) {
            logger.warn("Invalid JSON format for node update");
            return; //TODO: return: "HttpStatus.NOT_FOUND"; 404
        }

        // update the node
        this.nameServer.getIpMapLock().writeLock().lock();
        boolean updateStatus = this.nameServer.updateNode(nodeId, ip);
        if (!updateStatus) {//Node wasn't found => create new node
            this.nameServer.getIpMapLock().writeLock().unlock();
            this.nameServer.addNode(nodeId, ip); //TODO: return "HttpStatus.CREATED"; 201
        }
        this.nameServer.getIpMapLock().writeLock().unlock();
    }

    /**
     * Method for handling the POST request for the /ns/nodes/{nodeId} endpoint.<br>
     * Creates a new node with the given JSON String of a node.
     *
     * @param nodeJson JSON formatted String of the node to create
     * @return JSON formatted String with the link to the node.
     */
    @ResponseStatus(HttpStatus.CREATED) // 201
    @PostMapping(value = "/ns/nodes/{nodeId}", consumes = "application/json", produces = "application/json")
    public String postNode(@RequestBody String nodeJson) {
        logger.info("New post request");
        String name;    // name of the node
        String ip;      // ip of the node
        try {
            // parse the JSON string to get the name and IP address
            JSONObject node = (JSONObject) this.jsonParser.parse(nodeJson);
            name = node.get("name") != null ? (String) node.get("name") : null;
            ip = node.get("ip") != null ? (String) node.get("ip") : null;
            if (name == null) {
                logger.warn("Invalid JSON format for node creation: no name specified");
                throw new JSONInvalidFormatException("No name specified"); //return "HttpStatus.BAD_REQUEST" 400
            }
            if (ip == null) {
                logger.warn("Invalid JSON format for node creation: no ip specified");
                throw new JSONInvalidFormatException("No ip specified"); //return "HttpStatus.BAD_REQUEST" 400
            }
        } catch (ParseException e) {
            logger.warn("Invalid JSON format for node creation" + e.getMessage());
            throw new JSONInvalidFormatException(e.getMessage()); //return "HttpStatus.BAD_REQUEST" 400
        }
        int id = Hashing.hash(name);

        this.nameServer.getIpMapLock().writeLock().lock();
        boolean status = this.nameServer.addNode(id, ip);
        if (!status) {
            this.nameServer.getIpMapLock().writeLock().unlock();
            throw new NodeAlreadyExistsException(id, ip); // return HttpStatus.CONFLICT 409
        }
        String response = "{\"link\":\"/ns/nodes/" + id + "\"}";
        return response;
    }

    /**
     * Method for handling the DELETE request for the /ns/nodes/{nodeId} endpoint.<br>
     * Deletes the node with the given id.
     * @param nodeId id of the node to delete
     */
    @ResponseStatus(HttpStatus.OK) // 200
    @DeleteMapping("/ns/nodes/{nodeId}")
    public void deleteNode(@PathVariable int nodeId) {
        boolean status = this.nameServer.deleteNode(nodeId); //single call doesn't need lock
        if (!status) {
            throw new NodeNotFoundException(nodeId); // return HttpStatus.NOT_FOUND 404
        }
    }

    /**
     * Method for handling the DELETE request for the /ns/nodes/{nodeId}/fail endpoint.<br>
     * Deletes the node that failed from the map in the NameServer. Then it notifies the previous and next nodes with
     * respect to the node tha failed. Finally, it launches the FailureAgent for the node with the given id.
     * @param nodeId id of the node that failed
     */
    @ResponseStatus(HttpStatus.OK) // 200
    @DeleteMapping("/ns/nodes/{nodeId}/fail")
    public void failNode(@PathVariable int nodeId) {
        this.nameServer.getIpMapLock().writeLock().lock();
        boolean status = this.nameServer.deleteNode(nodeId);
        String message = "{\"nodeId\":" + nodeId + ",\"type\":\"Failure\"}";
        logger.info(message);
        try {
            if (status) {
                //notify neighbors of failed node
                DatagramPacket packetNext = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(this.nameServer.getNextNodeIP(nodeId)), DATAGRAM_PORT);
                this.socket.send(packetNext);
                DatagramPacket packetPrev = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(this.nameServer.getPrevNodeIP(nodeId)), DATAGRAM_PORT);
                this.socket.send(packetPrev);
            } else {
                // This node is not alive and someone reports a failure. (This is a duplicate message)
                // Thus, something might went wrong, notify everyone of node failure.
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("255.255.255.255"), DATAGRAM_PORT);
                this.socket.send(packet);
            }
        } catch (IOException ignored) {
        }
        this.nameServer.getIpMapLock().writeLock().unlock();

        this.nameServer.launchFailureAgent(nodeId);
    }

    /**
     * Method for handling the GET request for the /ns/files endpoint.<br>
     * Should return a list of the files in the system, but this is NOT IMPLEMENTED yet.
     */
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @GetMapping("/ns/files")
    public void getFiles() {
    }

    /**
     * Get the location of a certain file. The hash of the file is then calculated and based on
     * the hash the node on which the file should be stored is returned.
     * @param fileName name of the file to get the location of
     * @return the IP of the node that should store the file
     */
    @ResponseStatus(HttpStatus.OK) // 200
    @GetMapping("/ns/files/{fileName}")
    public String getFile(@PathVariable String fileName) {
        //this.logger.info("Request for file: " + fileName);
        int hash = Hashing.hash(fileName);
        return this.nameServer.getPrevNodeIP(hash);
    }

    /*
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @PutMapping("/ns/files/{fileName}")
    public void putFile(@PathVariable String fileName) {
    }

    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @PostMapping("/ns/files/{fileName}")
    public void postFile(@PathVariable String fileName) {
    }

    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @DeleteMapping("/ns/files/{fileName}")
    public void deleteFile(@PathVariable String fileName) {
    }
    */

    /**
     * Method for handling the GET request for the /ns/files/{fileName}/id endpoint.<br>
     * Returns the id of the node where the file with the given name is stored.
     * @param fileName name of the file to get the location of
     * @return the id of the node where the file is stored
     */
    @ResponseStatus(HttpStatus.OK) // 200
    @GetMapping("/ns/files/{fileName}/id")
    public int getFileId(@PathVariable String fileName) {
        //this.logger.info("Request for file: " + fileName);
        int hash = Hashing.hash(fileName);
        return this.nameServer.getPrevNode(hash);
    }

    /**
     * Prints the nodes that are currently registered in the NameServer, every 10 seconds.
     */
    @Scheduled(fixedRate = 10000)
    public void printMapping() {
        this.nameServer.getIpMapLock().readLock().lock();
        this.logger.info("Current mapping: ");
        for (Map.Entry<Integer, String> entry : this.nameServer.getIpMapping().entrySet()) {
            System.out.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
        }
        this.nameServer.getIpMapLock().readLock().unlock();
    }

    /**
     * Class for handling the UDP Discovery packets sent by the nodes that join the system. <br>
     * Automatic discovery of new nodes. Listens for broadcast packets.
     */
    private class DiscoveryHandler extends Thread {

        /**
         * Logger for the DiscoveryHandler class.
         */
        Logger logger = LoggerFactory.getLogger(DiscoveryHandler.class);

        /**
         * Pointer to the NameServerController.
         */
        NameServerController nameServerController;

        /**
         * Boolean to mark if the DiscoveryHandler is running.
         */
        boolean running = false;

        /**
         * Constructor for the DiscoveryHandler class.<br>
         * Initializes the socket that has to listen for broadcast packets.
         * @param nameServerController pointer to the NameServerController
         */
        public DiscoveryHandler(NameServerController nameServerController) {
            this.setDaemon(true); //make sure the thread dies when the main thread dies
            this.nameServerController = nameServerController;
            try {
                this.nameServerController.socket.setBroadcast(true);
                this.nameServerController.socket.setSoTimeout(888);
            } catch (SocketException e) {
                this.nameServerController.socket = null;
                logger.warn("Automatic node discovery disabled");
                e.printStackTrace();
            }
        }

        /**
         * Main method for the DiscoveryHandler thread.<br>
         * Listens for broadcast packets. If the type of packet equals to "Discovery", the node is added to the NameServer.
         * If the node was successfully added, the node is notified of the successful addition. The previous node is also
         * notified of the successful addition so it can update its replication table.
         */
        @Override
        public void run() {
            // return if socket is null
            if (this.nameServerController.socket == null) return;

            this.running = true;
            while (this.running) {
                boolean success = false;
                int Id = -1;
                try {
                    byte[] receiveBuffer = new byte[512]; //make a new buffer for every request (to overwrite the old one)
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    this.nameServerController.socket.receive(receivePacket);
                    String data = new String(receivePacket.getData()).trim();
                    String ip = receivePacket.getAddress().getHostAddress();    // ip of the node that sent the packet
                    String response;

                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) parser.parse(data);
                    String type = (String) jsonObject.get("type");

                    if (!type.equals("Discovery")) {
                        continue;
                    } //do not respond on non discovery packets

                    logger.info("Discovery package received! -> " + receivePacket.getAddress() + ":" + receivePacket.getPort());

                    String name = (String) jsonObject.get("name");      // name of the node that sent the packet

                    if (name == null) {
                        this.nameServerController.logger.info("Adding node failed");
                        response = "{\"status\":\"Access Denied\"}";        // send response to the node that it couldn't be added to the NameServer
                        success = false;
                    } else {
                        Id = Hashing.hash(name);
                        logger.info("Name: " + name);
                        logger.info("Hashed name: " + Id);
                        if (this.nameServerController.nameServer.addNode(Id, ip)) {
                            //adding successful
                            this.nameServerController.nameServer.getIpMapLock().readLock().lock();
                            response = "{" +                    // repond with the amount of nodes in the system and the hash of the node that was added
                                    "\"type\":\"NS-offer\"," +
                                    "\"status\":\"OK\"," +
                                    "\"nodeCount\":" + this.nameServerController.nameServer.getIpMapping().size() + "," +
                                    "\"node\":" + this.nameServerController.nameServer.nodeToJson(Id) + "}";
                            success = true;

                            this.nameServerController.nameServer.getIpMapLock().readLock().unlock();
                        } else {
                            //adding unsuccessful
                            this.nameServerController.logger.info("Adding node failed");
                            response = "{" +        // send response to the node that it couldn't be added to the NameServer
                                    "\"type\":\"NS-offer\"," +
                                    "\"status\":\"Access Denied\"" +
                                    "}";
                            success = false;
                        }
                    }

                    // port 8000
                    DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    this.nameServerController.socket.send(responsePacket);


                } catch (ParseException | IOException ignored) {
                    // Since we're working with the default socket timeout of 1 second, this exception is thrown a lot when there are no
                    // nodes entering the network.
                    //logger.warn("An error occurred while handling the discovery packet: "+ignored.getMessage());
                }

                //notify previous node from the newly added node to update his replication table
                if (success) {
                    //notify the previous node that a new node has been created
                    this.nameServerController.nameServer.getIpMapLock().readLock().lock();
                    String ip = this.nameServerController.nameServer.getIpMapping().get(Id);    // get the ip of the node that was added
                    String previousIp = this.nameServerController.nameServer.getPrevNodeIP(Id); // get the ip of the previous node
                    JSONObject json = new JSONObject();
                    json.put("id", Id);
                    json.put("ip", ip);
                    logger.info(previousIp + ":8081/files");
                    try {
                        logger.info("Status:"
                                + Unirest.post("http://" + previousIp + ":8081/files").body(json.toJSONString()).connectTimeout(5000).asString().getStatus()
                        );
                        this.nameServerController.nameServer.getIpMapLock().readLock().unlock();
                    } catch (UnirestException e) {
                        logger.warn("Unable to contact previous node");
                        this.nameServerController.nameServer.getIpMapLock().readLock().unlock();
                        //report failure of this node
                        failNode(this.nameServerController.nameServer.getPrevNode(Id));
                    }
                }
            }
        }

        /**
         * Stops the DiscoveryHandler thread.
         */
        public void terminate() {
            this.running = false;
        }
    }


    /**
     * Main method for the NameServerController thread.<br>
     * Used for testing purposes only.
     */
    public void run() {
        logger.info("Starting NameServer...");
        try {
            NameServerController nameServerController = new NameServerController();
            nameServerController.putNode(5, "192.168.0.5");
            nameServerController.putNode(6, "192.168.0.6");
            nameServerController.putNode(7, "192.168.0.7");
            nameServerController.putNode(8, "192.168.0.8");
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("NamingServer failed to start.");
        }
    }
}

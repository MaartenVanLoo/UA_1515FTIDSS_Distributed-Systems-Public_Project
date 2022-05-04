package NameServer;

import Utils.Hashing;
import kong.unirest.Unirest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import NameServer.NameServerStatusCodes.* ;
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

//Overview:
// /ns
//  --> /ns/nodes
//      --> /ns/nodes/{nodeId}
//      --> /ns/nodes/{nodeId}/fail
//      --> /ns/nodes/name/{name}
//  --> /ns/files
//      --> /ns/files/{fileName}

//usefull info:
//https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc
//https://www.restapitutorial.com/lessons/httpmethods.html#:~:text=The%20primary%20or%20most%2Dcommonly,but%20are%20utilized%20less%20frequently.
//https://developer.mozilla.org/en-US/docs/Web/HTTP/Methods/GET
//https://stackoverflow.com/questions/39835648/how-do-i-get-the-json-in-a-response-body-with-spring-annotaion
@CrossOrigin(origins = "*") //Use to allow access from any origin
@RestController
public class NameServerController {
    Logger logger = LoggerFactory.getLogger(NameServerController.class);


    static final int DATAGRAM_PORT = 8001;
    private NameServer nameServer;
    private JSONParser jsonParser = new JSONParser();

    DiscoveryHandler discoveryHandler;
    private DatagramSocket socket;

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

    public NameServer getNameServer() {
        return this.nameServer;
    }

    @ResponseStatus(HttpStatus.OK) //200
    @GetMapping(value = "/ns", produces = "application/json")
    public String getNameServerStatus() {
        this.nameServer.getIpMapLock().readLock().lock();
        String response =  "{\"Status\": \"running\","+
                "\"Utilities\":{" +
                    "\"Discovery\":\"" + (this.socket == null?"disabled":"enabled")+"\"" +
                "}," +
                "\"Nodes\":" + this.nameServer.getIpMapping().size() +"," +
                "\"Mapping\":[" +
                this.nameServer.getIpMapping().keySet().stream().map(s -> "{" + this.nameServer.nodeToString(s) + "}").collect(Collectors.joining(","))+
                "]}";
        this.nameServer.getIpMapLock().readLock().unlock();
        return response;
    }


    //<editor-fold desc="/ns/nodes">
    @ResponseStatus(HttpStatus.OK) // 200
    @GetMapping("/ns/nodes")
    public String getAllNodes() {
        this.nameServer.getIpMapLock().readLock().lock();
        String response = "{" + this.nameServer.getIpMapping().entrySet().stream().map(e -> e.getKey()+" => "+e.getValue()).collect(Collectors.joining("\n")) + "}";
        this.nameServer.getIpMapLock().readLock().unlock();
        return response;
    }

    @ResponseStatus(HttpStatus.OK) // 200
    @GetMapping(value = "/ns/nodes/{nodeId}", produces = "application/json")
    public String getNode(@PathVariable int nodeId) {
        return this.nameServer.nodeToJson(nodeId); //no need for lock as this is only 1 operation (every method is thread safe)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT) // 204
    @PutMapping("/ns/nodes/{nodeId}")
    public void putNode(@PathVariable int nodeId, @RequestBody String nodeJson) {
        String ip = "";
        try {
            JSONObject node = (JSONObject) this.jsonParser.parse(nodeJson);
            ip = node.get("ip")!=null?(String) node.get("ip"):null;
            if (ip == null)  throw new JSONInvalidFormatException("No ip specified"); //return "HttpStatus.BAD_REQUEST" 400
        } catch (ParseException e) {
            logger.warn("Invalid JSON format for node update");
            return; //TODO: return: "HttpStatus.NOT_FOUND"; 404
        }

        this.nameServer.getIpMapLock().writeLock().lock();
        boolean updateStatus = this.nameServer.updateNode(nodeId, ip);
        if (!updateStatus) {//Node wasn't found => create new node
            this.nameServer.getIpMapLock().writeLock().unlock();
            this.nameServer.addNode(nodeId, ip); //TODO: return "HttpStatus.CREATED"; 201
        }
        this.nameServer.getIpMapLock().writeLock().unlock();
    }

    @ResponseStatus(HttpStatus.CREATED) // 201
    @PostMapping(value = "/ns/nodes", consumes = "application/json", produces = "application/json")
    public String postNode(@RequestBody String nodeJson) {
        logger.debug("New post request");
        String name = "";
        String ip = "";
        try {
            JSONObject node = (JSONObject) this.jsonParser.parse(nodeJson);
            name = node.get("name")!=null?(String) node.get("name"):null;
            ip = node.get("ip")!=null?(String) node.get("ip"):null;
            if (name == null){
                logger.debug("Invalid JSON format for node creation: no name specified");
                throw new JSONInvalidFormatException("No name specified"); //return "HttpStatus.BAD_REQUEST" 400
            }
            if (ip == null){
                logger.debug("Invalid JSON format for node creation: no ip specified");
                throw new JSONInvalidFormatException("No ip specified"); //return "HttpStatus.BAD_REQUEST" 400
            }
        } catch (ParseException e) {
            logger.debug("Invalid JSON format for node creation" + e.getMessage());
            throw new JSONInvalidFormatException(e.getMessage()); //return "HttpStatus.BAD_REQUEST" 400
        }
        int id = Hashing.hash(name);

        this.nameServer.getIpMapLock().writeLock().lock();
        boolean status = this.nameServer.addNode(id, ip);
        if (!status) {
            this.nameServer.getIpMapLock().writeLock().unlock();
            throw new NodeAlreadyExistsException(id,ip); // return HttpStatus.CONFLICT 409
        }
        String response = "{\"link\":\"/ns/nodes/"+id+"\"}";
        this.nameServer.getIpMapLock().writeLock().unlock();

        //notify the previous node that a new node has been created
        this.nameServer.getIpMapLock().readLock().lock();
        String previousIp = this.nameServer.getPrevNodeIP(id);
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("ip", ip);
        Unirest.post(previousIp+":8081/files").body(json.toJSONString());
        this.nameServer.getIpMapLock().readLock().unlock();

        return response;
    }

    @ResponseStatus(HttpStatus.OK) // 200
    @DeleteMapping("/ns/nodes/{nodeId}")
    public void deleteNode(@PathVariable int nodeId) {
        boolean status = this.nameServer.deleteNode(nodeId); //single call doesn't need lock
        if (!status) {
            throw new NodeNotFoundException(nodeId); // return HttpStatus.NOT_FOUND 404
        }
    }

    @ResponseStatus(HttpStatus.OK) // 200
    @DeleteMapping("/ns/nodes/{nodeId}/fail")
    public void failNode(@PathVariable int nodeId) {
        this.nameServer.getIpMapLock().writeLock().lock();
        boolean status = this.nameServer.deleteNode(nodeId);
        String message = "{\"nodeId\":"+nodeId+",\"type\":\"Failure\"}";
        System.out.println(message);
        try {
            if (status) {
                //notify neighbors of failed node
                DatagramPacket packetNext = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(this.nameServer.getNextNodeIP(nodeId)), NameServer.DATAGRAM_PORT);
                this.socket.send(packetNext);
                DatagramPacket packetPrev = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(this.nameServer.getPrevNodeIP(nodeId)), NameServer.DATAGRAM_PORT);
                this.socket.send(packetPrev);
            }else{
                // This node is not alive and someone reports a failure. (This is a duplicate message)
                // Thus, something might went wrong, notify everyone of node failure.
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("255.255.255.255"), NameServer.DATAGRAM_PORT);
                this.socket.send(packet);
            }
        }
        catch (IOException ignored) {}
        this.nameServer.getIpMapLock().writeLock().unlock();
    }

    //</editor-fold>

    //<editor-fold desc="/ns/files">
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @GetMapping("/ns/files")
    public void getFiles() {}

    @ResponseStatus(HttpStatus.OK) // 200
    @GetMapping("/ns/files/{fileName}")
    /**
     * Get the location of a certain file. The hash of the file is then calculated and based on
     * the hash the node on which the file should be stored is returned.
     */
    public String getFile(@PathVariable String fileName) {
        this.logger.info("Request for file: " + fileName);
        int hash = Hashing.hash(fileName);
        return this.nameServer.getPrevNodeIP(hash);
    }

    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @PutMapping("/ns/files/{fileName}")
    public void putFile(@PathVariable String fileName){}

    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @PostMapping("/ns/files/{fileName}")
    public void postFile(@PathVariable String fileName){}

    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED) // 501
    @DeleteMapping("/ns/files/{fileName}")
    public void deleteFile(@PathVariable String fileName){}
    //</editor-fold>


    @Scheduled(fixedRate = 10000)
    public void printMapping() {
        this.nameServer.getIpMapLock().readLock().lock();
        this.logger.info("Current mapping: " + this.nameServer.getIpMapping());
        for (Map.Entry<Integer, String> entry : this.nameServer.getIpMapping().entrySet()) {
            System.out.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
        }
        this.nameServer.getIpMapLock().readLock().unlock();
    }

    /**
     *  Automatic discovery of new nodes. Listens for broadcast packets.
     */
    private class DiscoveryHandler extends Thread{
        NameServerController nameServerController;
        boolean running = false;


        private DiscoveryHandler(){}
        public DiscoveryHandler(NameServerController nameServerController) {
            this.setDaemon(true); //make sure the thread dies when the main thread dies
            this.nameServerController = nameServerController;
            try {
                this.nameServerController.socket.setBroadcast(true);
                this.nameServerController.socket.setSoTimeout(888);
            } catch (SocketException e) {
                this.nameServerController.socket = null;
                System.out.println("Automatic node discovery disabled");
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            if (this.nameServerController.socket == null) return;

            this.running = true;
            while (this.running) {
                try {
                    byte[] receiveBuffer = new byte[512]; //make a new buffer for every request (to overwrite the old one)
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    this.nameServerController.socket.receive(receivePacket);
                    String data = new String(receivePacket.getData()).trim();
                    String ip = receivePacket.getAddress().getHostAddress();
                    String response;

                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) parser.parse(data);
                    String type = (String) jsonObject.get("type");

                    if (!type.equals("Discovery")) {continue;} //do not respond on non discovery packets

                    System.out.println("Discovery package received! -> " + receivePacket.getAddress() + ":" + receivePacket.getPort());

                    String name = (String) jsonObject.get("name");
                    if (name == null) {
                        this.nameServerController.logger.info("Adding node failed");
                        response = "{\"status\":\"Access Denied\"}";
                    }else {
                        int Id = Hashing.hash(name);
                        System.out.println("Name: " + name);
                        System.out.println("Hashed name: " + Id);
                        if (this.nameServerController.nameServer.addNode(Id, ip)) {
                            //adding successful
                            this.nameServerController.nameServer.getIpMapLock().readLock().lock();
                            response = "{" +
                                    "\"type\":\"NS-offer\"," +
                                    "\"status\":\"OK\"," +
                                    "\"nodeCount\":" + this.nameServerController.nameServer.getIpMapping().size() + "," +
                                    "\"node\":" + this.nameServerController.nameServer.nodeToJson(Id) + "}";
                            this.nameServerController.nameServer.getIpMapLock().readLock().unlock();

                        } else {
                            //adding unsuccessful
                            this.nameServerController.logger.info("Adding node failed");
                            response = "{" +
                                    "\"type\":\"NS-offer\"," +
                                    "\"status\":\"Access Denied\"" +
                                    "}";
                        }
                    }
                    DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    this.nameServerController.socket.send(responsePacket);
                }
                catch (ParseException | IOException ignored) {}
            }
        }

        public void terminate(){
            this.running = false;
        }
    }



    // main method
    public void run() {
        System.out.println("Starting NameServer...");
        try {
            NameServerController nameServerController = new NameServerController();
            nameServerController.putNode(5, "192.168.0.5");
            nameServerController.putNode(6, "192.168.0.6");
            nameServerController.putNode(7, "192.168.0.7");
            nameServerController.putNode(8, "192.168.0.8");
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("NamingServer failed to start.");
        }
    }
}

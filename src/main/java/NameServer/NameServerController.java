package NameServer;

import Utils.Hashing;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

@RestController
public class NameServerController {
    Logger logger = LoggerFactory.getLogger(NameServerController.class);

    static final int DATAGRAM_PORT = 8001;

    private final String mappingFile = "nameServerMap.json";
    private final TreeMap<Integer,String> ipMapping = new TreeMap<>(); //id =>ip;//MOET PRIVATE!!!
    private ReadWriteLock ipMapLock = new ReentrantReadWriteLock();
    DiscoveryHandler discoveryHandler;
    private DatagramSocket socket;

    public NameServerController() {
        loadMapping();
        try {
            this.socket = new DatagramSocket(DATAGRAM_PORT);
        } catch (SocketException e) {
            logger.warn("Automatic discovery disabled, unable to create datagram socket");
            this.socket = null;
        }
        this.discoveryHandler = new DiscoveryHandler(this);
        discoveryHandler.start();
    }

    @RequestMapping(value = "/ns", method = RequestMethod.GET)
    public String getNameServer() {
        return "NameServer is running";
    }

    private void saveMapping(){
        try {
            saveMapping(this.mappingFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void loadMapping() {
        try {
            loadMapping(this.mappingFile);
        }catch (IOException e){
            System.out.println("File reading error:" + e.getMessage());
            System.out.println("Creating new file.");
            this.ipMapLock.writeLock().lock();
            this.ipMapping.clear();
            this.ipMapLock.writeLock().unlock();
            try {
                saveMapping(this.mappingFile);
            } catch (IOException ignored){}
            System.out.println("Starting with empty map.");
        }
        catch(ParseException e){
            System.out.println("File parsing error:" + e.getMessage());
            System.out.println("Creating new file.");
            this.ipMapLock.writeLock().lock();
            this.ipMapping.clear();
            this.ipMapLock.writeLock().unlock();
            try {
                saveMapping(this.mappingFile);
            } catch (IOException ignored){}
            System.out.println("Starting with empty map.");
        }
    }
    private void saveMapping(String filename) throws IOException {
        JSONObject jsonObject = new JSONObject();
        ipMapLock.readLock().lock();
            for (int key : ipMapping.keySet()) {
                //System.out.println(key + "->" + this.ipMapping.get(key));
                jsonObject.put(key, ipMapping.get(key));
            }
        ipMapLock.readLock().unlock();
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
        jsonObject.writeJSONString(out);
        out.flush();
        out.close();
    }
    private void loadMapping(String filename) throws FileNotFoundException, ParseException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));//new file(filename)
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
        ipMapLock.writeLock().lock();
        this.ipMapping.clear();
        for (Object obj : jsonObject.keySet()) {
            long key = Long.parseLong((String) obj);
            this.ipMapping.put((int) key, (String) jsonObject.get(obj));
        }
        ipMapLock.writeLock().unlock();
    }

    public boolean addNode(int Id, String ip){
        System.out.println("Adding node with id: " + Id + " and ip: " + ip);
        this.logger.info("Adding node with id: " + Id + " and ip: " + ip);
        ipMapLock.writeLock().lock(); //note: take write lock to avoid someone else changing the ipmap between changing the lock from read to write
        if (ipMapping.containsKey(Id)){
            ipMapLock.writeLock().unlock();
            return false;
        }
        this.ipMapping.put(Id, ip);
        try {
            saveMapping(this.mappingFile);
            ipMapLock.writeLock().unlock();
            return true;
        } catch (IOException exception) {
            exception.printStackTrace();
            ipMapLock.writeLock().unlock();
        }
        return false;
    }

    //location of the file (what node?)
    @GetMapping("/ns/getFile")
    public String getLocation(@RequestParam String fileName) {
        this.logger.info("Request for file: " + fileName);
        int hash = Hashing.hash(fileName);
        Map.Entry<Integer,String> entry;
        this.ipMapLock.readLock().lock();
        entry = this.ipMapping.floorEntry(hash-1); //searches for equal or lower than
        if (entry == null){ //no smaller key
            entry = this.ipMapping.lastEntry();// biggest
        }
        this.ipMapLock.readLock().unlock();
        return entry.getValue();
    }

    @GetMapping("/ns/getPrevIP")
    public String getPrevIP(@RequestParam int currentID) {
        ipMapLock.readLock().lock();
        this.logger.info("Request ip for previous node with id: " + currentID);
        int prevKey = ipMapping.lowerKey(currentID) != null ? ipMapping.lowerKey(currentID) :ipMapping.lastKey();
        String prevIP = ipMapping.get(prevKey);
        ipMapLock.readLock().unlock();
        return prevIP;
    }

    @GetMapping("/ns/getNextIP")
    public String getNextIP(@RequestParam int currentID) {
        ipMapLock.readLock().lock();
        this.logger.info("Request ip for next node with id: " + currentID);
        int nextKey = ipMapping.higherKey(currentID) != null ? ipMapping.higherKey(currentID) :ipMapping.firstKey();
        String nextIP = ipMapping.get(nextKey);
        ipMapLock.readLock().unlock();
        return nextIP;
    }

    @GetMapping("/ns/getNodeIP")
    public String getIP(@RequestParam int id){
        ipMapLock.readLock().lock();
        this.logger.info("Request ip for node with id: " + id);
        if (!ipMapping.containsKey(id)) return null;
        String ip = ipMapping.get(id);
        ipMapLock.readLock().unlock();
        return ip;
    }

    @DeleteMapping("/ns/removeNode")
    public ResponseEntity<String> removeNode(@RequestParam int Id){
        //System.out.println("Removing node with id: " + Id);
        this.logger.info("Removing node with id: " + Id);
        ResponseEntity<String> response;
        this.ipMapLock.writeLock().lock();  //note: take write lock to avoid someone else changing the ipmap between changing the lock from read to write
        if (this.ipMapping.containsKey(Id)) {
            this.ipMapping.remove(Id);
            try {
                saveMapping(this.mappingFile);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            response = new ResponseEntity<>("Node with id: " + Id + " removed", HttpStatus.OK);
        } else{
            response = new ResponseEntity<>("Node with id: " + Id + " does not exist", HttpStatus.NOT_FOUND);
        }
        this.ipMapLock.writeLock().unlock();
        return response;
    }

    @PutMapping("/ns/updateNode")
    public boolean updateNode(@RequestParam int Id,@RequestParam String ip){
        System.out.println("Updating node with id: " + Id);
        this.logger.info("Updating node with id: " + Id);
        this.ipMapLock.writeLock().lock();
        if (!this.ipMapping.containsKey(Id)) return false;
        this.ipMapping.put(Id, ip);

        try {
            saveMapping(this.mappingFile);
        } catch (IOException exception) {
            exception.printStackTrace();
            this.ipMapLock.writeLock().unlock();
            return false;
        }
        this.ipMapLock.writeLock().unlock();
        return true;
    }

    @DeleteMapping("/ns/nodeFailure")
    public ResponseEntity<String> nodeFailure(@RequestParam int Id) {
        this.logger.info("Node with id: " + Id + " failed");

        //remove existing node from ip mapping
        this.ipMapLock.writeLock().lock();
        if (!this.ipMapping.containsKey(Id)) return new ResponseEntity<>("Node with id: " + Id + " does not exist", HttpStatus.NOT_FOUND);
        this.ipMapping.remove(Id);
        if (this.ipMapping.isEmpty()) return new ResponseEntity<>("No nodes left", HttpStatus.NOT_FOUND);
        try {
            saveMapping(this.mappingFile);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        this.logger.info(this.ipMapping.size() + " nodes left");
        this.ipMapLock.writeLock().unlock();

        try {
            this.ipMapLock.readLock().lock();
            Integer prevId = ipMapping.lowerKey(Id) != null ? ipMapping.lowerKey(Id) : ipMapping.lastKey();
            Integer nextId = ipMapping.higherKey(Id) != null ? ipMapping.higherKey(Id) : ipMapping.firstKey();
            this.ipMapLock.readLock().unlock();
            String nextIP = getNextIP(Id);
            String prevIP = getPrevIP(Id);

            //Update the next node
            String message = "{\"type\":\"Failure\"," +
                    "\"failed\":" + Id + "," +
                    "\"prevNodeId\":" + prevId + "," +
                    "\"prevNodeIP\":\"" + prevIP + "\"}";
            DatagramPacket packet = new DatagramPacket(message.getBytes(StandardCharsets.UTF_8), message.length(), InetAddress.getByName(nextIP), 8001);
            this.socket.send(packet);


            //update the prev node
            message = "{\"type\":\"Failure\"," +
                    "\"failed\":" + Id + "," +
                    "\"nextNodeId\":" + nextId + "," +
                    "\"nextNodeIP\":\"" + nextIP + "\"}";
            packet = new DatagramPacket(message.getBytes(StandardCharsets.UTF_8), message.length(), InetAddress.getByName(prevIP), 8001);
            this.socket.send(packet);
            return new ResponseEntity<>("Node with id: " + Id + " failed", HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>("IO Exception thrown", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Unknown exception thrown", HttpStatus.BAD_REQUEST);
        }
    }


    public TreeMap<Integer,String> getIdMap(){
        return this.ipMapping;
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    @GetMapping("/ns/validateNode")
    public String validationData(@RequestParam int Id){
        this.logger.info("Node with id: " + Id + " is validating");
        this.ipMapLock.readLock().lock();
        String thisIp = this.ipMapping.get(Id);
        String nextIp = getNextIP(Id);
        String prevIp = getPrevIP(Id);
        Integer nextId = ipMapping.higherKey(Id) != null ? ipMapping.higherKey(Id) :ipMapping.firstKey();
        Integer prevId = ipMapping.lowerKey(Id) != null ? ipMapping.lowerKey(Id) :ipMapping.lastKey();
        this.ipMapLock.readLock().unlock();
        return "{\"type\":\"Validate\"," +
                "\"id\":" + Id + "," +
                "\"nextNodeIP\":\"" + nextIp + "\"," +
                "\"prevNodeIP\":\"" + prevIp + "\"," +
                "\"nextNodeId\":" + nextId + "," +
                "\"prevNodeId\":" + prevId + "}";
    }

    @Scheduled(fixedRate = 10000)
    public void printMapping() {
        ipMapLock.readLock().lock();
        this.logger.info("Current mapping: " + this.ipMapping);
        for (Map.Entry<Integer, String> entry : this.ipMapping.entrySet()) {
            System.out.println("Key: " + entry.getKey() + " Value: " + entry.getValue());
        }
        ipMapLock.readLock().unlock();
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
                    System.out.println("Discovery package received! -> " + receivePacket.getAddress() + ":" + receivePacket.getPort());
                    String data = new String(receivePacket.getData()).trim();
                    String ip = receivePacket.getAddress().getHostAddress();
                    String response;

                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) parser.parse(data);
                    String name = (String) jsonObject.get("name");
                    if (name == null) {
                        this.nameServerController.logger.info("Adding node failed");
                        response = "{\"status\":\"Access Denied\"}";
                    }else {
                        int Id = Hashing.hash(name);

                        if (this.nameServerController.addNode(Id, ip)) {
                            //adding successful
                            this.nameServerController.ipMapLock.readLock().lock();
                            Integer lowerId = this.nameServerController.getIdMap().lowerKey(Id - 1);
                            if (lowerId == null) lowerId = this.nameServerController.getIdMap().lastKey();
                            Integer higherId = this.nameServerController.getIdMap().higherKey(Id + 1);
                            if (higherId == null) higherId = this.nameServerController.getIdMap().firstKey();

                            response = "{" +
                                    "\"type\":\"NS-offer\"," +
                                    "\"status\":\"OK\"," +
                                    "\"id\":" + Id + "," +
                                    "\"nodeCount\":" + this.nameServerController.getIdMap().size() + "," +
                                    "\"prevNodeId\":" + lowerId + "," +
                                    "\"nextNodeId\":" + higherId + "}";
                            this.nameServerController.ipMapLock.readLock().unlock();

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
            nameServerController.addNode(5, "192.168.0.5");
            nameServerController.addNode(6, "192.168.0.6");
            nameServerController.addNode(7, "192.168.0.7");
            nameServerController.addNode(8, "192.168.0.8");
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("NamingServer failed to start.");
        }
    }
}

package NameServer;

import Utils.Hashing;
import org.hibernate.property.access.internal.PropertyAccessStrategyIndexBackRefImpl;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.*;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;


@RestController
public class NameServer {
    Logger logger = LoggerFactory.getLogger(NameServer.class);

    static final int DATAGRAM_PORT = 8001;

    private final String mappingFile = "nameServerMap.json";
    private final TreeMap<Integer,String> ipMapping = new TreeMap<>(); //id =>ip;//MOET PRIVATE!!!
    ReadWriteLock ipMapLock = new ReentrantReadWriteLock();
    DiscoveryHandler discoveryHandler;

    public NameServer() {
        loadMapping();
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
        int prevKey = ipMapping.lowerKey(currentID) != null ? ipMapping.lowerKey(currentID) :ipMapping.lastKey();
        String prevIP = ipMapping.get(prevKey);
        ipMapLock.readLock().unlock();
        return prevIP;
    }

    @GetMapping("/ns/getNextIP")
    public String getNextIP(@RequestParam int currentID) {
        ipMapLock.readLock().lock();
        int nextKey = ipMapping.higherKey(currentID) != null ? ipMapping.higherKey(currentID) :ipMapping.firstKey();
        String nextIP = ipMapping.get(nextKey);
        ipMapLock.readLock().unlock();
        return nextIP;
    }

    @DeleteMapping("/ns/removeNode")
    public void removeNode(@RequestParam int Id){
        //System.out.println("Removing node with id: " + Id);
        this.logger.info("Removing node with id: " + Id);
        this.ipMapLock.writeLock().lock();  //note: take write lock to avoid someone else changing the ipmap between changing the lock from read to write
        if (this.ipMapping.containsKey(Id)) {
            this.ipMapping.remove(Id);
            try {
                saveMapping(this.mappingFile);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        this.ipMapLock.writeLock().unlock();
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
            this.discoveryHandler.socket.send(packet);


            //update the prev node
            message = "{\"type\":\"Failure\"," +
                    "\"failed\":" + Id + "," +
                    "\"nextNodeId\":" + nextId + "," +
                    "\"nextNodeIP\":\"" + nextIP + "\"}";
            packet = new DatagramPacket(message.getBytes(StandardCharsets.UTF_8), message.length(), InetAddress.getByName(prevIP), 8001);
            this.discoveryHandler.socket.send(packet);
            return new ResponseEntity<>("Node with id: " + Id + " failed", HttpStatus.OK);
        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>("Node with id: " + Id + " failed", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("Node with id: " + Id + " failed", HttpStatus.BAD_REQUEST);
        }
    }
    public TreeMap<Integer,String> getIdMap(){
        return this.ipMapping;
    }

    /**
     *  Automatic discovery of new nodes. Listens for broadcast packets.
     */
    private class DiscoveryHandler extends Thread{
        NameServer nameServer;
        boolean running = false;
        DatagramSocket socket;

        private DiscoveryHandler(){}
        public DiscoveryHandler(NameServer nameServer) {
            this.setDaemon(true); //make sure the thread dies when the main thread dies
            this.nameServer = nameServer;
            try {
                this.socket = new DatagramSocket(DATAGRAM_PORT);
                this.socket.setBroadcast(true);
                this.socket.setSoTimeout(888);
            } catch (SocketException e) {
                this.socket = null;
                System.out.println("Automatic node discovery disabled");
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            if (this.socket == null) return;

            this.running = true;
            while (this.running) {
                try {
                    byte[] receiveBuffer = new byte[512]; //make a new buffer for every request (to overwrite the old one)
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    this.socket.receive(receivePacket);
                    System.out.println("Discovery package received! -> " + receivePacket.getAddress() + ":" + receivePacket.getPort());
                    String data = new String(receivePacket.getData()).trim();
                    String ip = receivePacket.getAddress().getHostAddress();
                    String response;

                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) parser.parse(data);
                    String name = (String) jsonObject.get("name");
                    if (name == null) {
                        this.nameServer.logger.info("Adding node failed");
                        response = "{\"status\":\"Access Denied\"}";
                    }else {
                        int Id = Hashing.hash(name);

                        if (this.nameServer.addNode(Id, ip)) {
                            //adding successful
                            this.nameServer.ipMapLock.readLock().lock();
                            Integer lowerId = this.nameServer.getIdMap().lowerKey(Id - 1);
                            if (lowerId == null) lowerId = this.nameServer.getIdMap().lastKey();
                            Integer higherId = this.nameServer.getIdMap().higherKey(Id + 1);
                            if (higherId == null) higherId = this.nameServer.getIdMap().firstKey();

                            response = "{" +
                                    "\"type\":\"NS-offer\"," +
                                    "\"status\":\"OK\"," +
                                    "\"id\":" + Id + "," +
                                    "\"nodeCount\":" + this.nameServer.getIdMap().size() + "," +
                                    "\"prevNodeId\":" + lowerId + "," +
                                    "\"nextNodeId\":" + higherId + "}";
                            this.nameServer.ipMapLock.readLock().unlock();

                        } else {
                            //adding unsuccessful
                            this.nameServer.logger.info("Adding node failed");
                            response = "{" +
                                    "\"type\":\"NS-offer\"," +
                                    "\"status\":\"Access Denied\"" +
                                    "}";
                        }
                    }
                    DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    this.socket.send(responsePacket);
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
            NameServer nameServer = new NameServer();
            nameServer.addNode(5, "192.168.0.5");
            nameServer.addNode(6, "192.168.0.6");
            nameServer.addNode(7, "192.168.0.7");
            nameServer.addNode(8, "192.168.0.8");
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("NamingServer failed to start.");
        }
    }
}

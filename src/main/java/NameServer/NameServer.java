package NameServer;

import java.io.*;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.security.MessageDigest;

import org.json.simple.*;
import org.json.simple.parser.*;
import org.springframework.web.bind.annotation.*;


@RestController
public class NameServer {
    static final int DATAGRAM_PORT = 8001;

    private final String mappingFile = "nameServerMap.json";
    private final TreeMap<Integer,String> ipMapping = new TreeMap<>(); //id =>ip;//MOET PRIVATE!!!
    private final DiscoveryHandler discoveryHandler = new DiscoveryHandler(this);

    public NameServer() throws IOException {
        //Bri'ish init
        try {
            readMapFromFile(this.mappingFile);
        }catch (IOException e){
            System.out.println("File reading error:" + e.getMessage());
            System.out.println("Creating new file.");
            this.ipMapping.clear();
            writeMapToFile(this.mappingFile);
            System.out.println("Starting with empty map.");
        }
        catch(ParseException e){
            System.out.println("File parsing error:" + e.getMessage());
            System.out.println("Creating new file.");
            this.ipMapping.clear();
            writeMapToFile(this.mappingFile);
            System.out.println("Starting with empty map.");
        }
        discoveryHandler.start();
    }

    public int hash(String string)  {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(string.getBytes());
        } catch (Exception e){return -1;}
        String stringHash = new String(messageDigest.digest());
        //System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(stringHash.getBytes()));
        long max = 2147483647;
        long min = -2147483648;
        return (int)(((long)stringHash.hashCode()+max)*(32768.0/(max+Math.abs(min))));
    }
    private void writeMapToFile(String filename) throws IOException {
        JSONObject jsonObject = new JSONObject();
        synchronized (this.ipMapping) {
            for (int key : ipMapping.keySet()) {
                //System.out.println(key + "->" + this.ipMapping.get(key));
                jsonObject.put(key, ipMapping.get(key));
            }
        }
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
        jsonObject.writeJSONString(out);
        out.flush();
        out.close();

    }
    private void readMapFromFile(String filename) throws FileNotFoundException, ParseException {

        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
        synchronized (this.ipMapping) {
            this.ipMapping.clear();
            for (Object obj : jsonObject.keySet()) {
                long key = Long.parseLong((String) obj);
                this.ipMapping.put((int) key, (String) jsonObject.get(obj));
            }
        }
    }

    public boolean addNode(int Id, String ip){
        synchronized (this.ipMapping) {
            if (ipMapping.containsKey(Id)) return false;
            this.ipMapping.put(Id, ip);
            try {
                writeMapToFile(this.mappingFile);
                return true;
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        return false;
    }

    //location of the file (what node?)
    @GetMapping("/ns/getFile")
    public String getLocation(@RequestParam String fileName) {
        int hash =hash(fileName);
        Map.Entry<Integer,String> entry;
        entry = this.ipMapping.floorEntry(hash-1); //searches for equal or lower than
        if (entry == null){ //no smaller key
            entry = this.ipMapping.lastEntry();// biggest
        }
        return entry.getValue();
    }

    @DeleteMapping("/ns/removeNode")
    public void removeNode(@RequestParam int Id){
        synchronized (this.ipMapping) {
            if (this.ipMapping.containsKey(Id)) {
                this.ipMapping.remove(Id);
                try {
                    writeMapToFile(this.mappingFile);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    @PutMapping("/ns/updateNode")
    public boolean updateNode(@RequestParam int Id,@RequestParam String ip){
        synchronized (this.ipMapping) {
            if (!this.ipMapping.containsKey(Id)) return false;
            this.ipMapping.put(Id, ip); //
            try {
                writeMapToFile(this.mappingFile);
            } catch (IOException exception) {
                exception.printStackTrace();
                return false;
            }
            return true;
        }
    }

    public TreeMap<Integer,String> getIdMap(){
        return this.ipMapping;
    }

    private class DiscoveryHandler extends Thread{
        NameServer nameServer;
        boolean running = false;
        DatagramSocket socket = new DatagramSocket(DATAGRAM_PORT);

        private DiscoveryHandler() throws SocketException {}
        public DiscoveryHandler(NameServer nameServer) throws SocketException {
            this.nameServer = nameServer;
            this.socket.setBroadcast(true);
            this.socket.setSoTimeout(888);
        }

        @Override
        public void run() {
            this.running = true;
            byte[] receiveBuffer = new byte[512];
            byte[] responseBuffer = new byte[512];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            while (this.running) {
                try {
                    this.socket.receive(receivePacket);
                    System.out.println("Discovery package received! -> " + receivePacket.getAddress() + ":" + receivePacket.getPort());
                    String data = new String(receivePacket.getData()).trim();

                    int Id = this.nameServer.hash(data);
                    String ip = receivePacket.getAddress().getHostAddress();
                    String response;
                    if (this.nameServer.addNode(Id,ip)){
                        //adding successful
                        response = Integer.toString(Id);
                    }else{
                        //adding unsuccessful
                        response = "Access Denied";
                    }
                    DatagramPacket responsePacket = new DatagramPacket(response.getBytes(StandardCharsets.UTF_8), response.length(), receivePacket.getAddress(), receivePacket.getPort());
                    this.socket.send(responsePacket);

                } catch (IOException ignore) {}
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

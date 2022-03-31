package NameServer;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * NameServer for managing the active nodes in the network.
 * All methods are thread-safe.
 */
public class NameServer {
    Logger logger = LoggerFactory.getLogger(NameServer.class);

    static final int DATAGRAM_PORT = 8001;

    private final String mappingFile = "nameServerMap.json";
    private final TreeMap<Integer,String> ipMapping = new TreeMap<>(); //id =>ip;//MOET PRIVATE!!!
    private ReadWriteLock ipMapLock = new ReentrantReadWriteLock();

    public NameServer() {
        loadMapping();
    }

    /**
     * Saves the mapping to the default file.
     */
    private void saveMapping(){
        try {
            saveMapping(this.mappingFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load the mapping from the default file.
     */
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

    /**
     * Save the mapping to the given file.
     * @param filename File to save the mapping to.
     * @throws IOException If the file cannot be written to.
     */
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

    /**
     * Load the mapping from the given file.
     * @param filename File to load the mapping from.
     * @throws FileNotFoundException If the file cannot be found.
     * @throws ParseException If the file cannot be parsed by the JSON parser.
     */
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

    //<editor-fold desc="node control">
    /**
     * Get the ip address of a node with the given id.
     * @param id Node id
     * @return ip address of the node or null if the node is not registered
     */
    public String getNode(int id){
        ipMapLock.readLock().lock();
        String ip = ipMapping.get(id);
        ipMapLock.readLock().unlock();
        return ip;
    }

    /**
     * Set the ip address of a node with the given id.
     * If the node is already registered nothing will change.
     * @param id Node id.
     * @param ip ip address of the node.
     * @return True when the node was registered, false when the node was already registered
     */
    public boolean setNode(int id, String ip){
        ipMapLock.writeLock().lock();
        if (ipMapping.containsKey(id)) return false;
        ipMapping.put(id, ip);
        ipMapLock.writeLock().unlock();
        return true;
    }

    /**
     * Update the ip address of a node with the given id.
     * If the node is not registered nothing will change.
     * @param id Node id.
     * @param ip ip address of the node.
     * @return True when the node was updated, false when the node was not registered
     */
    public boolean updateNode(int id, String ip){
        ipMapLock.writeLock().lock();
        if (!ipMapping.containsKey(id)) return false;
        ipMapping.put(id, ip);
        ipMapLock.writeLock().unlock();
        return true;
    }

    /**
     * Remove the a node with the given id.
     * @param id Node id.
     * @return True when the node was removed, false when the node was not registered
     */
    public boolean removeNode(int id){
        ipMapLock.writeLock().lock();
        if (!ipMapping.containsKey(id)) return false;
        ipMapping.remove(id);
        ipMapLock.writeLock().unlock();
        return true;
    }

    /**
     * Get the id of a node with an Id stricly higher than the given id. Unless the given id is the highest registered id.
     * Then the id of the node with the lowest id is returned.
     * @param id Node id.
     * @return Id of the next node.
     */
    public int getNextNode(int id){
        ipMapLock.readLock().lock();
        int next = ipMapping.higherKey(id) != null ? ipMapping.higherKey(id) : ipMapping.firstKey();
        ipMapLock.readLock().unlock();
        return next;
    }

    /**
     * Get the id of a node with an Id stricly lower than the given id. Unless the given id is the lowest registered id.
     * Then the id of the node with the highest id is returned.
     * @param id Node id.
     * @return Id of the previous node.
     */
    public int getPrevNode(int id){
        ipMapLock.readLock().lock();
        int prev = ipMapping.lowerKey(id) != null ? ipMapping.lowerKey(id) : ipMapping.lastKey();
        ipMapLock.readLock().unlock();
        return prev;
    }

    /**
     * Get the node as json string. The full node identification is returned.
     * This includes the ip address and the id for the node and its neighbours.
     * @param id Node id.
     * @return json string of the node.
     */
    public String nodeToJson(int id){
        ipMapLock.readLock().lock();
        String json = "{" +
                "\"node:\"{" + nodeToString(id) + "}," +
                "\"next:\"{" + nodeToString(getNextNode(id)) + "}," +
                "\"prev:\"{" + nodeToString(getPrevNode(id)) + "}" +
                "}";
        ipMapLock.readLock().unlock();
        return json;
    }

    /**
     * Get the node as string. Note that the string is json compatible but not a full json string.
     * @param id Node id.
     * @return String of the node.
     */
    public String nodeToString(int id){
        ipMapLock.readLock().lock();
        return "\"id\": " + id + ", " +
                "\"ip\": \"" + getNode(id) + "\"";
    }
    //</editor-fold>

    //<editor-fold desc="getters & setters">

    /**
     * Get the mapped ip's
     * @return Mapped ip's
     */
    public TreeMap<Integer, String> getIpMapping() {
        return ipMapping;
    }

    /**
     * Get lock for synchronized access to the ip mapping
     * @return Lock for synchronized access to the ip mapping
     */
    public ReadWriteLock getIpMapLock() {
        return ipMapLock;
    }

    //</editor-fold>
}

package NameServer;

import Agents.FailureAgent;
import kong.unirest.Unirest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import NameServer.NameServerStatusCodes.*;

//TODO: Add write to file on updates

/**
 * NameServer for managing the active nodes in the network.
 * All methods are thread-safe.
 */
public class NameServer {

    /**
     * Logger for this class.
     */
    private final Logger logger = LoggerFactory.getLogger(NameServer.class);
    /**
     * Default file where the info of the nodes is stored
     */
    private final String mappingFile = "nameServerMap.json";
    /**
     * Map of the nodes in the network and their IPs.
     */
    private final TreeMap<Integer, String> ipMapping = new TreeMap<>();
    /**
     * Read/write lock for locking resources while reading of writing them
     */
    private ReadWriteLock ipMapLock = new ReentrantReadWriteLock();

    /**
     * Constructor of the NameServer. Loads the info of the nodes stored in the mappingFile.
     */
    public NameServer() {
        loadMapping();
    }

    /**
     * Saves the mapping to the default file.
     */
    private void saveMapping() {
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
        this.ipMapLock.writeLock().lock();
        try {
            loadMapping(this.mappingFile);
        } catch (IOException e) {
            // when the file can't be found / doesn't exist
            logger.warn("File reading error:" + e.getMessage());
            logger.info("Creating new file.");
            this.ipMapping.clear();
            try {
                saveMapping(this.mappingFile);
            } catch (IOException ignored) {
            }
            logger.info("Starting with empty map.");
        } catch (ParseException e) {
            // when the file is not in the correct format (JSON)
            logger.warn("File parsing error:" + e.getMessage());
            logger.info("Creating new file.");
            this.ipMapping.clear();
            try {
                saveMapping(this.mappingFile);
            } catch (IOException ignored) {
            }
            logger.info("Starting with empty map.");
        }
        this.ipMapLock.writeLock().unlock();
    }

    /**
     * Save the mapping to the given file.
     *
     * @param filename File to save the mapping to.
     * @throws IOException If the file cannot be written to.
     */
    private void saveMapping(String filename) throws IOException {
        JSONObject jsonObject = new JSONObject();
        ipMapLock.readLock().lock();
        for (int key : ipMapping.keySet()) {
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
     *
     * @param filename File to load the mapping from.
     * @throws FileNotFoundException If the file cannot be found.
     * @throws ParseException        If the file cannot be parsed by the JSON parser.
     */
    private void loadMapping(String filename) throws FileNotFoundException, ParseException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
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

    /**
     * Get the ip address of a node with the given id.
     *
     * @param id Node id
     * @return ip address of the node or null if the node is not registered
     */
    public String getNode(int id) {
        ipMapLock.readLock().lock();
        String ip = ipMapping.get(id);
        ipMapLock.readLock().unlock();
        return ip;
    }

    /**
     * Set the ip address of a node with the given id (and add the node to the TreeMap if it isn't in there yet).
     * If the node is already registered nothing will change.
     *
     * @param id Node id.
     * @param ip ip address of the node.
     * @return True when the node was registered, false when the node was already registered
     */
    public boolean addNode(int id, String ip) {
        ipMapLock.writeLock().lock();
        if (ipMapping.containsKey(id)) {
            ipMapLock.writeLock().unlock();
            return false;
        }
        ipMapping.put(id, ip);
        saveMapping();
        ipMapLock.writeLock().unlock();
        return true;
    }

    /**
     * Update the ip address of a node with the given id.
     * If the node is not registered nothing will change.
     *
     * @param id Node id.
     * @param ip ip address of the node.
     * @return True when the node was updated, false when the node was not registered
     */
    public boolean updateNode(int id, String ip) {
        ipMapLock.writeLock().lock();
        if (!ipMapping.containsKey(id)) {
            ipMapLock.writeLock().unlock();
            return false;
        }
        ipMapping.put(id, ip);
        saveMapping();
        ipMapLock.writeLock().unlock();
        return true;
    }

    /**
     * Remove the a node with the given id.
     *
     * @param id Node id.
     * @return True when the node was removed, false when the node was not registered
     */
    public boolean deleteNode(int id) {
        ipMapLock.writeLock().lock();
        if (!ipMapping.containsKey(id)) {
            ipMapLock.writeLock().unlock();
            return false;
        }
        ipMapping.remove(id);
        saveMapping();
        ipMapLock.writeLock().unlock();
        return true;
    }

    /**
     * Get the id of a node with an Id strictly higher than the given id. Unless the given id is the highest registered id.
     * Then the id of the node with the lowest id is returned.
     *
     * @param id Node id.
     * @return Id of the next node.
     */
    public int getNextNode(int id) {
        ipMapLock.readLock().lock();
        if (ipMapping.isEmpty()) {
            ipMapLock.readLock().unlock();
            return -1;
        }
        int next = ipMapping.higherKey(id) != null ? ipMapping.higherKey(id) : ipMapping.firstKey();
        ipMapLock.readLock().unlock();
        return next;
    }

    /**
     * Get the IP of a node with an Id strictly higher than the given id. Unless the given id is the highest registered id.
     * Then the IP of the node with the lowest id is returned.
     *
     * @param id Node id.
     * @return IP of the next node.
     */
    public String getNextNodeIP(int id) {
        ipMapLock.readLock().lock();
        if (ipMapping.isEmpty()) {
            ipMapLock.readLock().unlock();
            return null;
        }
        String next = ipMapping.higherKey(id) != null ? ipMapping.get(ipMapping.higherKey(id)) : ipMapping.get(ipMapping.firstKey());
        ipMapLock.readLock().unlock();
        return next;
    }

    /**
     * Get the id of a node with an Id strictly lower than the given id. Unless the given id is the lowest registered id.
     * Then the id of the node with the highest id is returned.
     *
     * @param id Node id.
     * @return Id of the previous node.
     */
    public int getPrevNode(int id) {
        ipMapLock.readLock().lock();
        if (ipMapping.isEmpty()) {
            ipMapLock.readLock().unlock();
            return -1;
        }
        int prev = ipMapping.lowerKey(id) != null ? ipMapping.lowerKey(id) : ipMapping.lastKey();
        ipMapLock.readLock().unlock();
        return prev;
    }

    /**
     * Get the IP of a node with an Id lower higher than the given id. Unless the given id is the lowest registered id.
     * Then the IP of the node with the highest id is returned.
     *
     * @param id Node id.
     * @return IP of the next node.
     */
    public String getPrevNodeIP(int id) {
        ipMapLock.readLock().lock();
        if (ipMapping.isEmpty()) {
            ipMapLock.readLock().unlock();
            return null;
        }
        String prev = ipMapping.lowerKey(id) != null ? ipMapping.get(ipMapping.lowerKey(id)) : ipMapping.get(ipMapping.lastKey());
        ipMapLock.readLock().unlock();
        return prev;
    }

    /**
     * Get the node as json string. The full node identification is returned.
     * This includes the ip address and the id for the node and its neighbours.
     *
     * @param id Node id.
     * @return json string of the node.
     */
    public String nodeToJson(int id) {
        ipMapLock.readLock().lock();
        JSONObject json = new JSONObject();

        if (!this.ipMapping.containsKey(id)) {
            throw new NodeNotFoundException("Node with id " + id + " can not be found on the network", ipMapLock.readLock());
        }
        JSONObject node = new JSONObject();
        node.put("id", id);
        node.put("ip", getNode(id));
        json.put("node", node);
        JSONObject next = new JSONObject();
        next.put("id", getNextNode(id));
        next.put("ip", getNode(getNextNode(id)));
        json.put("next", next);
        JSONObject prev = new JSONObject();
        prev.put("id", getPrevNode(id));
        prev.put("ip", getNode(getPrevNode(id)));
        json.put("prev", prev);
        ipMapLock.readLock().unlock();
        return json.toString();
    }

    /**
     * Get the node as string. Note that the string is json compatible but not a full json string.
     *
     * @param id Node id.
     * @return String of the node.
     */
    public String nodeToString(int id) {
        ipMapLock.readLock().lock();
        String json = "\"id\": " + id + ", " +
                "\"ip\": \"" + getNode(id) + "\"";
        ipMapLock.readLock().unlock();
        return json;
    }

    /**
     * Launch the failure agent.
     *
     * @param failedNodeId Id of the failed node.
     */
    public void launchFailureAgent(int failedNodeId) {
        this.ipMapLock.readLock().lock();
        try {
            FailureAgent agent = new FailureAgent(failedNodeId, this.getIpMapping());
            int nextNode = this.getNextNode(failedNodeId);
            String nextIp = this.ipMapping.get(nextNode);
            agent.setFirstNode(nextNode);

            logger.info("Bytestream of the FailureAgent: " + agent.serialize());
            int status = Unirest.post("http://" + nextIp + ":8081/agent").body(agent.serialize()).asString().getStatus();
            if (status == 200) {
                logger.info("Failure agent successfully launched.\nFailure agent send to " + nextNode + "\t" + nextIp);
            } else {
                logger.error("Failed to launch failure agent");
            }
        } catch (Exception e) {
            logger.error("Failed to launch failure agent. \n" + e.getMessage());
        }
        this.ipMapLock.readLock().unlock();
    }

    /**
     * Get the mapped ip's
     *
     * @return Mapped ip's
     */
    public TreeMap<Integer, String> getIpMapping() {
        return ipMapping;
    }

    /**
     * Get lock for synchronized access to the ip mapping
     *
     * @return Lock for synchronized access to the ip mapping
     */
    public ReadWriteLock getIpMapLock() {
        return ipMapLock;
    }

}

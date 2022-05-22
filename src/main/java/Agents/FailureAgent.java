package Agents;

import Node.*;
import Utils.Hashing;
import kong.unirest.Unirest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.stream.Collectors;

import Node.FileManager;

public class FailureAgent implements Runnable, Serializable {
    private static final long serialVersionUID = 1L;

    private  Node node;
    private long failedNodeId;
    private FileManager fileManager;


    String starterNodeIP;


    public FailureAgent(Node node, long failedNodeId) {
        this.node = node;
        this.failedNodeId = failedNodeId;
        run();
    }

    /**
     * Initialization of the agent
     */
    public void setup() {

        //get list of local files in the list from SyncAgent
        //if failing node is owner of the file replicate elsewhere
        ArrayList<String> arrayList = this.node.getSyncAgent().getFileList();
        for (String fileName : arrayList) {
            int fileHash = Hashing.hash(fileName);
            // ask the namingserver for the location of the file
            int nodeIDofFile = Integer.parseInt(Unirest.get("/ns/files/" + fileName + "/id").asString().getBody());
            // if the the failing node is the owner or the failing node was the owner,...
            String allNodes = Unirest.get("/ns/nodes").asString().getBody();
            allNodes = allNodes.replace("{", "").replace("}","");
            String[] allNodesList = allNodes.split("\n");
            TreeMap<Long, String> map = new TreeMap<>();
            for (String e: allNodesList){
                String[] temp = e.trim().split("=>");
                map.put(Long.parseLong(temp[0]),temp[1]);
            }
            //if (!map.containsKey(failedNodeId)) map.put(failedNodeId, "temp");
            if (map.floorKey((long) fileHash) == failedNodeId) {

            }
            //get the Id before and after the failed node Id if node isnt lastKey
                if (map.lastKey()==failedNodeId) {
                    // check if the fimehash is smalller than the last key but bigger than the second to last key
                    if (fileHash < map.lastKey() && fileHash > map.lowerKey((long) map.lastKey())) {
                        //file must be replicated
                        String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                                .routeParam("filename", fileName).asString().getBody();
                        long replicateId = Integer.parseInt(Unirest.get("/ns/files/{filename}/id").routeParam("filename", fileName).asString().getBody());
                        FileTransfer.sendFile(fileName, "local", "replica", replicateIPAddr);//
                        //update log file
                        fileManager.updateLogFile(fileName, replicateId, replicateIPAddr);
                        //send file to log
                        FileTransfer.sendFile(fileName + ".log", "log", "log", replicateIPAddr);
                    }

                }



        }
    }

    @Override
    public void run() {
        setup();
    }
}

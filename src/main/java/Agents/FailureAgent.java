package Agents;

import Node.Node;
import Utils.Hashing;
import kong.unirest.Unirest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.TreeMap;

import Node.FileManager;
import Node.FileTransfer;

/**
 * Failiure agent will be setup by the nameserver and then send to every node in the network.
 * Each node will execute the "run" method which should update all the files.
 */
public class FailureAgent implements Runnable, Serializable {
    private static final long serialVersionUID = 1L;

    private Node node = null;
    private final int failedNodeId;
    private int firstNode = 0; //node who recieves the agent the first time, agent should never pass by the same node twice
    private  TreeMap<Integer, String> allNodes = null;

    public FailureAgent( long failedNodeId, TreeMap<Integer,String> allNodes) {
        this.failedNodeId = (int)failedNodeId;
        this.allNodes = new TreeMap<Integer,String>(allNodes); //deepcopy!
    }
    public void setNode(Node node){
        this.node = node;
    }
    public void setFirstNode(int node){
        this.firstNode = node;
    }

    @Override
    public void run() {
        //setup
        File[] localFiles = getLocalFiles();            if(localFiles == null) return;
        File[] replicatedFiles = getReplicatedFiles();  if (replicatedFiles == null) return;
        TreeMap<Integer,String> dummyMap = getDummyMap();

        //update localFiles:
        //Check if the replicated node == failed node when looking in dummy map!
        for (File file : localFiles){
            int hash = Hashing.hash(file.getName());
            int targetNode = (int)(dummyMap.lowerKey(hash) != null ? dummyMap.lowerKey(hash) : dummyMap.lastKey());

            if (targetNode == this.failedNodeId){
                //failed node was owner of the replica! replica is lost!
                recreateReplica(file);
            }else if (targetNode == this.node.getId()){
                int oldPrevNode = (int)(dummyMap.lowerKey(hash) != null ? dummyMap.lowerKey(hash) : dummyMap.lastKey());
                if (failedNodeId == oldPrevNode) {
                    //Edge case where you are the "owner and targetNode" and the failed node was your previous node!
                    recreateReplica(file);
                }
            }
        }

        //update replicas
        //if this node contains a replica of a local file from the failed node this replica should be deleted
        for (File file: replicatedFiles){
            long origin = FileManager.getOrigin(file.getName());
            if (origin == -1){
                //failed to find origin in logfile => remove file
                System.out.println("FailureAgent:\tFailed to find origin in logfile, removing replica");
                this.node.getSyncAgent().deleteLocalFile(file.getName());
                file.delete();
                continue;
            }
            if (origin == failedNodeId){
                System.out.println("FailureAgent:\tRemoving file from failed node " + file.getName());
                File logFile = new File(FileManager.logFolder + "/" + file.getName() + ".log");
                this.node.getSyncAgent().deleteLocalFile(file.getName());
                file.delete();
                logFile.delete();
            }
        }

        //done updating, now send the agent to the next node
        if (this.node.getNextNodeId() == this.firstNode){
            //done sending the agent around
            System.out.println("FailureAgent:\tdone.");
            return;
        }
        try {
            String nextIP = this.node.getNextNodeIP();
            this.node = null; //Note: this is needed because a "node" object is not serializable
            int status = Unirest.post("http://" + nextIP + ":8081/agent").body(this.serialize()).asString().getStatus();
            if (status == 200){
                System.out.println("FailureAgent:\tSuccessfully sent agent to next node.");
            } else {
                System.out.println("FailureAgent:\tFailed to send agent to next node.");
            }
        }catch (Exception e){
            e.printStackTrace();
            System.out.println("FailureAgent:\tError in serialization, failed to forward failure agent");
        }
    }

    private File[] getLocalFiles(){
        if (this.node == null){
            return null;
        }
        return this.node.getFileManager().getLocalFiles();
    }
    private File[] getReplicatedFiles(){
        if (this.node == null){
            return null;
        }
        return this.node.getFileManager().getReplicatedFiles();
    }
    private TreeMap<Integer,String> getDummyMap(){
        TreeMap<Integer,String> map = new TreeMap<>(this.allNodes);
        map.put((int)this.failedNodeId,"");
        return map;
    }

    private void recreateReplica(File localFile){
        try {
            //find target node for file
            String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                    .routeParam("filename", localFile.getName()).asString().getBody();
            long replicateId = Integer.parseInt(Unirest.get("/ns/files/{filename}/id")
                    .routeParam("filename", localFile.getName()).asString().getBody());
            if (Objects.equals(replicateIPAddr, node.getIP())) {
                replicateIPAddr = this.node.getPrevNodeIP();
                replicateId = this.node.getPrevNodeId();
            }

            //create new logfile
            System.out.println("FailureAgent:\tCreating new logfile for " + localFile.getName());
            this.node.getFileManager().createLogFile(FileManager.logFolder + "/"+ localFile.getName() + ".log");
            System.out.println("FailureAgent:\tUpdating logfile for " + localFile.getName() + "target node: " + replicateIPAddr);
            this.node.getFileManager().updateLogFile(localFile.getName(),replicateId,replicateIPAddr);


            //send file and logfile
            FileTransfer.sendFile(localFile.getName(),FileManager.localFolder,FileManager.replicaFolder,replicateIPAddr);
            FileTransfer.sendFile(localFile.getName() + ".log",FileManager.logFolder,FileManager.logFolder, replicateIPAddr);

            //remove log file from this node
            File logFile = new File(FileManager.logFolder + "/" + localFile.getName() + ".log");
            logFile.delete();
        }catch (Exception e){
            e.printStackTrace();
            System.out.println("FailureAgent:\tFailed to recreate replica");
        }
    }

    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        byte[] agent = baos.toByteArray();
        oos.close();
        return agent;
    }
    public static FailureAgent deserialize(byte[] agent) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(agent);
        ObjectInputStream ois = new ObjectInputStream(bais);
        FailureAgent fa = (FailureAgent) ois.readObject();
        ois.close();
        return fa;
    }
}

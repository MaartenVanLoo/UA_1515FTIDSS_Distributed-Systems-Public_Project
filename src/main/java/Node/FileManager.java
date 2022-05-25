package Node;

import Utils.Hashing;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.nio.file.*;
import java.util.stream.Collectors;

public class FileManager extends Thread {
    private Node node;
    final String localFolder = "local";
    final String replicaFolder = "replica";
    final String logFolder = "log";
    private ArrayList<String> fileList = new ArrayList<>();

    WatchService watchService = FileSystems.getDefault().newWatchService();
    Path path = Paths.get("./local");

    private static final int SENDING_PORT = 8004;


    public FileManager(Node node) throws IOException {
        super("FileManager");
        this.node = node;
        this.setDaemon(true);
        this.start();
    }

    public synchronized void startup() {
        try {
            while (!this.node.isSetUp()) {
                Thread.sleep(100); // wait for the node to be set up
            }
            int nodeCount = getNodeCount();
            String launchDirectory = System.getProperty("user.dir"); // get the current directory
            System.out.println("Current directory: " + launchDirectory); // print the current directory
            File dir = new File(launchDirectory + "/" + localFolder); // get the  new directory(/local)
            System.out.println("Directory: " + dir.getCanonicalPath()); // print the directory
            File[] files = dir.listFiles(); // get the files in the directory
            if (files == null || files.length == 0) {// if there are no files in the directory
                System.out.println("No files in local folder"); // print that there are no files in the directory
                return;
            }
            for (File file : files) { // for each file in the directory
                System.out.println("File: " + file.getName()); // print the file name
            }
            System.out.println("NodeCount: " + nodeCount); // print the node count
            // Get the names of the files by using the .getName() method
            for (File file : files) {
                //System.out.println(file.getName());
                //int filehash = Hashing.hash(file.getName()); //  nameserver doet dit
                fileList.add(file.getName());
                File logFile = new File(launchDirectory + "/"+ logFolder + "/" + file.getName() + ".log");
                if (!logFile.exists()) {
                    logFile.createNewFile();
                }
                JSONObject source = new JSONObject();
                JSONObject owner = new JSONObject();
                source.put("title", "source");
                source.put("ip", this.node.getIP());
                source.put("id", this.node.getId());
                owner.put("title", "owner");
                owner.put("ip", this.node.getIP());
                owner.put("id", this.node.getId());
                JSONObject logfile = new JSONObject();
                JSONArray downloads = new JSONArray();
                logfile.put("owner", owner);
                logfile.put("origin", source);
                logfile.put("Downloads", downloads);
                //put the JSONObject in the file
                try (PrintWriter out = new PrintWriter(new FileWriter(logFile, false))){
                    out.write(logfile.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (nodeCount == 1){
                    //just copy from local to replica folders
                    System.out.println("Copying "+ file.getName() +" to replica folder");
                    File localFile=new File(launchDirectory + "/" + localFolder + "/" + file.getName());
                    File replicaFile = new File(launchDirectory + "/" + replicaFolder + "/" + file.getName());
                    Files.copy(localFile.toPath(), replicaFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }
                //send fileName to NameServer
                try {
                    String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                            .routeParam("filename", file.getName()).asString().getBody();
                    long replicateId = Integer.parseInt(Unirest.get("/ns/files/{filename}/id").routeParam("filename", file.getName()).asString().getBody());
                    // if the IP addr the NS sent back is the same as the one of this node, get the prev node IP address
                    // check example 3 doc3.pdf
                    if (Objects.equals(replicateIPAddr, node.getIP())) {
                        replicateIPAddr = this.node.getPrevNodeIP();
                        replicateId = this.node.getPrevNodeId();
                    }


                    System.out.println("Replicating " + file.getName() + " to " + replicateIPAddr);
                    //send file to replica
                    FileTransfer.sendFile(file.getName(), localFolder, replicaFolder, replicateIPAddr);//
                    //update log file
                    this.updateLogFile(file.getName(), replicateId, replicateIPAddr);
                    //send file to log
                    FileTransfer.sendFile(file.getName() + ".log", logFolder, logFolder, replicateIPAddr);
                    //delete log file
                    File f = new File(logFolder + "/" + file.getName() + ".log");
                    f.delete();

                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public synchronized  void update(){
        File[] replicatedFiles = this.getReplicatedFiles();
        for (File file : replicatedFiles){
            try {
                String fileName = file.getName();
                String replicateIPAddr = Unirest.get("/ns/files/{filename}").routeParam("filename", file.getName()).asString().getBody();
                long replicateId = Integer.parseInt(Unirest.get("/ns/files/{filename}/id").routeParam("filename", file.getName()).asString().getBody());
                if (targetIsOrigin(fileName, replicateIPAddr)){
                    replicateId = getPrevNode(replicateId);
                    replicateIPAddr = getNodeIp(replicateId);
                }
                if (replicateId != this.node.getId()){
                    //update log file
                    this.updateLogFile(fileName, replicateId, replicateIPAddr);
                    //send log file
                    FileTransfer.sendFile(fileName + ".log", logFolder, logFolder, replicateIPAddr);
                    //send file
                    FileTransfer.sendFile(fileName, replicaFolder, replicaFolder, replicateIPAddr);
                    //delete log file
                    File f = new File(logFolder + "/" + fileName + ".log");
                    f.delete();
                    //delete file
                    file.delete();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public synchronized void updateFileLocationsNewNextNode(long nextNodeId, String nextNodeIp){
        this.update();
        return;/*
        // new node with given ID is inserted in the network, check the hash of every replicated file.
        // If the hash > the nextNodeId => the file must be send to this new node and removed from this one.

        //Warning: when updating file location when a single node is in the network => prev ID is also yourself!
        //no files should be updated!
        System.out.println("Prev id" + this.node.getPrevNodeId());
        System.out.println("Next id" + this.node.getNextNodeId());
        System.out.println("new node id" + nextNodeId);
        int nodeCount = getNodeCount();
        System.out.println("Node count" + nodeCount);
        if (nodeCount == 1){
        //if ((this.node.getId() == this.node.getPrevNodeId() || this.node.getId() == this.node.getNextNodeId()) && this.node.getId() == nextNodeId){
            System.out.println("Only one node in the network, no files should be updated!");
            return;
        }
        if (nodeCount == 2){
        //if (this.node.getPrevNodeId() == this.node.getNextNodeId()){
            System.out.println("Only 2 nodes in the network!");
            //2 nodes in the network => send all replicas
            for (File file : this.getReplicatedFiles()){
                FileTransfer.sendFile(file.getName(), replicaFolder, replicaFolder, nextNodeIp);
                file.delete();
                //update log file
                updateLogFile(file.getName(),nextNodeId, nextNodeIp);
                System.out.println("LogFile updated" );
                //send log file
                FileTransfer.sendFile(file.getName() + ".log", logFolder,logFolder, nextNodeIp);
                //delete log file
                File logFile = new File(logFolder + "/" + file.getName() + ".log");
                logFile.delete();
            }
            return;
        }
        //3 or more nodes
        try {
            File[] files = this.getReplicatedFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files in replica folder");
                return;
            }
            // Get the names of the files by using the .getName() method
            for (File file : files) {
                int fileHash = Hashing.hash(file.getName());
                System.out.println("Filename: " + file.getName() + "\tHash: " + fileHash + "\tTransfer:" + hasToSendFile(this.node.getId(), nextNodeId, fileHash));

                if (hasToSendFile(this.node.getId(), nextNodeId, fileHash)) {
                    //send fileName to new node
                    try {
                        String replicateIPAddr = Unirest.get("/ns/files/{filename}").routeParam("filename", file.getName()).asString().getBody();
                        long replicateID = -1;
                        //assert
                        System.out.println("Replicate IP: " + replicateIPAddr + "\tNextNodeIP: " + nextNodeIp);

                        if (Objects.equals(replicateIPAddr, node.getIP())) {
                            //don't send it, file is correctly placed
                            continue;
                        }

                        // check if the target of the file is the origin of the file
                        if (this.targetIsOrigin(file.getName(), replicateIPAddr)) {
                            //new node = in between this node and next node => new node should become the owner of the file
                            replicateID = nextNodeId;
                            replicateIPAddr = nextNodeIp;
                        }else{
                            replicateID = Long.parseLong(Unirest.get("/ns/files/{fileName}/id").routeParam("fileName", file.getName()).asString().getBody());
                        }



                        FileTransfer.sendFile(file.getName(), replicaFolder, replicaFolder, replicateIPAddr);
                        file.delete();
                        //update log file
                        updateLogFile(file.getName(),replicateID, replicateIPAddr);
                        System.out.println("LogFile updated" );
                        //send log file
                        FileTransfer.sendFile(file.getName() + ".log", logFolder,logFolder, replicateIPAddr);
                        //delete log file
                        File logFile = new File(logFolder + "/" + file.getName() + ".log");
                        logFile.delete();

                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        */
    }
    private boolean hasToSendFile(int own_id, long next_id, int fileHash){
        if (next_id > own_id){
            // this node should only have files between own_id and next_id
            /* below = simplified statement according to intellij
            if (fileHash > own_id && fileHash < next_id){
                return false; //no resending
            }
            return true;
             */
            return fileHash <= own_id || fileHash >= next_id; //no resending
        }else{
            //warning! wrap around. All files between the next node and the current node should not be held by the current node
            /*below = simplified statement according to intellij
            if (fileHash > next_id && fileHash < own_id){
                return true;
            }
            return false;
             */
            return fileHash > next_id && fileHash < own_id;
        }
    }

    public synchronized void updateFileLocationOtherNewNode(long newNodeId){
        update();
        /*
        int nodeCount = getNodeCount();
        if (nodeCount == 1){
            System.out.println("Only one node in the network, no files should be updated!");
            return;
        }
        if (nodeCount == 2){
            System.out.println("Two nodes in the network, no files should be updated");
            return;
        }
        //1 and 2 nodes in the network will always trigger "updateFileLocationNewNextNode"
        //check if other node is NOT between current ID and next ID
        if (!IsBetween(this.node.getId(), this.node.getNextNodeId(), newNodeId)){
            //This could potentially trigger the edgecase where the new node should take the ownership of the
            // replicated file stored in the previous node of the original node.
            for (File file: this.getReplicatedFiles()){
                int hash = Hashing.hash(file.getName());
                if (IsBetween(this.node.getId(), this.node.getNextNodeId(), hash)){
                    //this file should be stored in this node, don't take action
                    continue;
                }
                //It shouldn't be possible to own a file with a hash larger than the next node id unless the next node
                // is the origin and owner of the file (edge case)
                // We need to check if this file should be send to the new node
                String replicateIPAddr = Unirest.get("/ns/files/{filename}").routeParam("filename", file.getName()).asString().getBody();
                if (this.targetIsOrigin(file.getName(),replicateIPAddr)){
                    //Don't send file, target is still the origin
                    continue;
                }
                long replicateID = Long.parseLong(Unirest.get("/ns/files/{fileName}/id").routeParam("fileName", file.getName()).asString().getBody());

                //if all checks are passed this means we have to send the file to the new node
                FileTransfer.sendFile(file.getName(), replicaFolder, replicaFolder, replicateIPAddr);
                file.delete();
                //update log file
                updateLogFile(file.getName(),replicateID, replicateIPAddr);
                System.out.println("LogFile updated" );
                //send log file
                FileTransfer.sendFile(file.getName() + ".log", logFolder,logFolder, replicateIPAddr);
                //delete log file
                File logFile = new File(logFolder + "/" + file.getName() + ".log");
                logFile.delete();
            }
        }

        */
    }

    private boolean IsBetween(long first, long second, long value){
        if (first < second){
            return value >= first && value <= second;
        }
        return value >= second && value <= first;
    }
    private int getNodeCount(){
        try {
            String nameserver = Unirest.get("/ns").asString().getBody();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(nameserver);
            return (int)((long) json.get("Nodes"));
        }
        catch (Exception e){
            return -1;
        }

    }
    private int getPrevNode(long nodeId){
        try {
            //Request nodeId configuration from nameserver
            String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(nodeId)).asString().getBody();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(response);
            JSONObject prevNode = (JSONObject) json.get("prev");
            return (int)((long) prevNode.get("id"));
        }
        catch (Exception e){
            return -1;
        }
    }
    private String getNodeIp(long nodeId){
        try{
            String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(nodeId)).asString().getBody();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(response);
            JSONObject prevNode = (JSONObject) json.get("node");
            return (String) prevNode.get("ip");
        }
        catch (Exception e){
            return "";
        }
    }

    /**
     * Sends a given file to a given IP address.
     *
     * @param file   the file to be sent
     * @param ipAddr the IP address to send the file to
     * @throws IOException
     */
    public void startReplication(File file, String ipAddr) throws IOException {
        Socket replicateSocket = new Socket(ipAddr, SENDING_PORT);

        FileInputStream fis = null;
        BufferedInputStream bis = null;
        OutputStream os = null;

        try {
            byte[] buffer = new byte[(int) file.length()];
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            bis.read(buffer, 0, buffer.length);
            os = replicateSocket.getOutputStream();

            System.out.println("Sending " + file.getName() + "(" + buffer.length + " bytes)");
            long time = System.currentTimeMillis();
            os.write(buffer, 0, buffer.length);
            os.flush();
            System.out.println("Request processed: " + time);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        } finally {
            if (os != null) os.close();
            if (bis != null) bis.close();
            if (fis != null) fis.close();
            if (replicateSocket != null) replicateSocket.close();
        }
    }

    //https://www.baeldung.com/java-nio2-watchservice
    public void checkDirectory() throws IOException, InterruptedException {
        //TODO: add log file handling
        path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        WatchKey key;
        while ((key = watchService.take()) != null) {

            //sleep(50);
            for (WatchEvent<?> event : key.pollEvents()) {
                //sleep(50);
                //fileEvents.add(event.kind().toString() + " " + event.context().toString());
                System.out.println(
                        "Event kind:" + event.kind()
                                + ". File affected: " + event.context() + ".");

                File file = new File(event.context().toString());
                String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                        .routeParam("filename", file.getName()).asString().getBody();
                long replicateId = Long.parseLong(Unirest.get("/ns/files/{filename}/id")
                        .routeParam("filename", file.getName()).asString().getBody());
                if (Objects.equals(replicateIPAddr, node.getIP())) {
                    replicateIPAddr = this.node.getPrevNodeIP();
                    replicateId = this.node.getPrevNodeId();
                }
                switch (event.kind().toString()) {
                    case "ENTRY_CREATE":
                        //Create log file
                        JSONObject owner  = new JSONObject();
                        owner.put("id", this.node.getId());
                        owner.put("ip", this.node.getIP());
                        JSONObject origin = new JSONObject();
                        origin.put("id", this.node.getId());
                        origin.put("ip", this.node.getIP());
                        JSONArray downloads = new JSONArray();

                        JSONObject logfile = new JSONObject();
                        logfile.put("owner", owner);
                        logfile.put("origin", origin);
                        logfile.put("Downloads", downloads);

                        FileWriter writer = new FileWriter(event.context().toString() + ".log");
                        writer.write(logfile.toJSONString());
                        writer.close();
                        this.updateLogFile(file.getName(),replicateId,replicateIPAddr);
                        FileTransfer.sendFile(file.getName() + ".log", logFolder,logFolder, replicateIPAddr);
                        //[fallthrough]
                    case "ENTRY_MODIFY":
                        try {
                            System.out.println("Replicating " + file.getName() + " to " + replicateIPAddr); //vieze ai zeg
                            //send file to replica
                            FileTransfer.sendFile(file.getName(), localFolder, replicaFolder, replicateIPAddr);
                            System.out.println("Modification handled");
                        } catch (Exception e) {
                            System.out.println("Modification Error: " + e.getMessage() + " File:" + file.getName());
                        }
                        break;
                    case "ENTRY_DELETE":
                       try {
                            FileTransfer.deleteFile(file.getName(), replicaFolder, replicateIPAddr);
                            FileTransfer.deleteFile(file.getName() + ".log", logFolder, replicateIPAddr);
                            System.out.println("Deletion handled");
                        }catch(Exception e){
                            System.out.println("Deletion Error: " + e.getMessage() + " File:" + file.getName());
                        }
                        break;
                }
            }
            key.reset();
        }
    }

    public synchronized void shutDown() {
        String launchDirectory = System.getProperty("user.dir");
        System.out.println("Current directory: " + launchDirectory); //vieze ai zeg
        File dir = new File(launchDirectory + "/" + localFolder); //get the local folder
        //System.out.println("Directory: " + dir.getCanonicalPath()); //vieze ai zeg
        File[] files = dir.listFiles(); //get all files in the directory
        if (files == null || files.length == 0) {
            System.out.println("No files in local folder"); //vieze ai zeg
            return;
        }
        for (File file : files) {
            System.out.println("File: " + file.getName()); //vieze ai zeg
        }
        // Remove replications of local files
        for (File file : files) {
            //send fileName to NameServer
            try {
                String deleteIPAddr = Unirest.get("/ns/files/{filename}") //vieze ai zeg
                        .routeParam("filename", file.getName()).asString().getBody(); //vieze ai zeg
                // if the IP addr the NS sent back is the same as the one of this node, get the prev node IP address
                // check example 3 doc3.pdf
                if (Objects.equals(deleteIPAddr, node.getIP())) {
                    deleteIPAddr = this.node.getPrevNodeIP();
                }

                System.out.println("Deleting " + file.getName() + " to " + deleteIPAddr);
                //delete file to replica
                FileTransfer.deleteFile(file.getName(), replicaFolder, deleteIPAddr);
                //delete log file
                FileTransfer.deleteFile(file.getName() + ".log", logFolder, deleteIPAddr);

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        //send replica files to prev node
        dir = new File(launchDirectory + "/" + replicaFolder);
        //System.out.println("Directory: " + dir.getCanonicalPath());
        files = dir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files in replica folder");
            return;
        }
        for (File file : files) {
            //send fileName to NameServer
            String replicateIPAddr = this.node.getPrevNodeIP();
            long replicateId = this.node.getPrevNodeId();

            //check edge case
            if (this.targetIsOrigin(file.getName(), replicateIPAddr)) {
                String target = "";
                try {
                    //get target node information
                    JSONParser parser = new JSONParser();

                    target = Unirest.get("/ns/nodes/{id}").routeParam("id", String.valueOf(replicateId)).asString().getBody();
                    JSONObject targetNode = (JSONObject) parser.parse(target);
                    replicateIPAddr = (String) ((JSONObject) targetNode.get("prev")).get("ip");
                    replicateId = (long) ((JSONObject) targetNode.get("prev")).get("id");
                }catch (Exception e) {
                    System.out.println("Error in parsing target node information" + target);
                    e.printStackTrace();
                }
            }
            System.out.println("Replicating " + file.getName() + " to " + replicateIPAddr);

            //send file to replica
            FileTransfer.sendFile(file.getName(), replicaFolder, replicaFolder, replicateIPAddr);
            //delete file
            file.delete();
            //update log file
            updateLogFile(file.getName(),replicateId, replicateIPAddr);
            //send log file
            FileTransfer.sendFile(file.getName() + ".log", logFolder,logFolder, replicateIPAddr);
            //delet logfile
            File logFile = new File(logFolder + "/" + file.getName() + ".log");
            logFile.delete();
        }
    }

    /**
     * If the "./local" and "./replica" folders don't exist yet, then this method will create them to avoid future errors. Also checks if the logfolder exists and creates it if it doesn't.
     */
    void createDirectories() {
        //check if local directory exists
        File dir = new File(localFolder);
        if (!dir.exists()) {
            dir.mkdir();
        }
        //check if replica directory exists
        dir = new File(replicaFolder);
        if (!dir.exists()) {
            dir.mkdir();
        }
        //check if log directory exists
        dir = new File(logFolder);
        if (!dir.exists()) {
            dir.mkdir();
        }
    }

    @Override
    public void run() {
        this.createDirectories();
        this.startup();
        try {
            checkDirectory();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public File[] getLocalFiles() {
        File local = new File("./local");
        return local.listFiles();
    }

    public File[] getReplicatedFiles() {
        File local = new File("./replica");
        return local.listFiles();
    }

    public void updateLogFile(String fileName, long newOwner, String newIP) {
        String logFileContent = "";
        try {
            //load log file
            File logFile = new File( logFolder + "/"+ fileName + ".log");

            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            JSONParser parser = new JSONParser();
            logFileContent = reader.lines().collect(Collectors.joining(System.lineSeparator()));
            JSONObject jsonObject = (JSONObject) parser.parse(logFileContent);
            reader.close();
            //update log file
            JSONObject owner = (JSONObject) jsonObject.get("owner");
            owner.put("id", newOwner);
            owner.put("ip", newIP);
            jsonObject.put("owner", owner);
            //write log file
            FileWriter writer = new FileWriter(logFile);
            writer.write(jsonObject.toJSONString());
            writer.close();
        } catch (Exception e){
            System.out.println("Error in updating log file\n" + logFileContent);
            e.printStackTrace();
        }
    }

    public boolean targetIsOrigin(String fileName, String targetIP) {
        try {
            //read file
            File logFile = new File(logFolder + "/" + fileName + ".log");
            String logFileContent = "";
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            //parse json file
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
            reader.close();
            //check if target is owner
            JSONObject origin = (JSONObject) jsonObject.get("origin");
            if (origin.get("ip").equals(targetIP)) {
                System.out.println("Target is origin");
                return true;
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }
}



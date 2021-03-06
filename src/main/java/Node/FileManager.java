package Node;

import kong.unirest.Unirest;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.util.stream.Collectors;

/**
 * FileManager class is used to manage the files on the node.
 */
public class FileManager extends Thread {

    /**
     * The node that the file manager is associated with.
     */
    private Node node;

    /**
     * The folder where the local files of the node are stored.
     */
    public final static String localFolder = "local";

    /**
     * The folder where the replicated files of other nodes are stored.
     */
    public final static String replicaFolder = "replica";

    /**
     * The folder where the log files of the replicated files are stored.
     */
    public final static String logFolder = "log";

    /**
     * A WatchService object used to monitor the local folder for changes.
     */
    WatchService watchService = FileSystems.getDefault().newWatchService();

    /**
     * The path to the folder the WatchService is monitoring.
     */
    Path path = Paths.get("./local");

    /**
     * The constructor of the FileManager class.
     * @param node The node that the file manager is associated with.
     * @throws IOException If the filesystem doesn't support a WatchService.
     */
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
            System.out.println("FileManager:\tCurrent directory: " + launchDirectory); // print the current directory
            File dir = new File(launchDirectory + "/" + localFolder); // get the  new directory(/local)
            System.out.println("FileManager:\tDirectory: " + dir.getCanonicalPath()); // print the directory
            File[] files = dir.listFiles(); // get the files in the directory
            if (files == null || files.length == 0) {// if there are no files in the directory
                System.out.println("FileManager:\tNo files in local folder"); // print that there are no files in the directory
                return;
            }
            for (File file : files) { // for each file in the directory
                System.out.println("FileManager:\tFile: " + file.getName()); // print the file name
            }
            System.out.println("FileManager:\tNodeCount: " + nodeCount); // print the node count
            // Get the names of the files by using the .getName() method
            for (File file : files) {
                //create logfile
                File logFile = new File(launchDirectory + "/" + logFolder + "/" + file.getName() + ".log");
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
                logfile.put("downloads", downloads);
                //put the JSONObject in the file
                try (PrintWriter out = new PrintWriter(new FileWriter(logFile, false))) {
                    out.write(logfile.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (nodeCount == 1) {
                    //just copy from local to replica folders
                    System.out.println("FileManager:\tCopying " + file.getName() + " to replica folder");
                    File localFile = new File(launchDirectory + "/" + localFolder + "/" + file.getName());
                    File replicaFile = new File(launchDirectory + "/" + replicaFolder + "/" + file.getName());
                    Files.copy(localFile.toPath(), replicaFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    this.updateLogFile(file.getName(), this.node.getId(), this.node.getIP());
                    continue;
                }

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


                    System.out.println("FileManager:\tReplicating " + file.getName() + " to " + replicateIPAddr);
                    //send file to replica
                    FileTransfer.sendFile(file.getName(), localFolder, replicaFolder, replicateIPAddr);
                    //update log file
                    this.updateLogFile(file.getName(), replicateId, replicateIPAddr);
                    //send file to log
                    FileTransfer.sendFile(file.getName() + ".log", logFolder, logFolder, replicateIPAddr);
                    //delete log file
                    File f = new File(logFolder + "/" + file.getName() + ".log");
                    f.delete();

                } catch (Exception e) {
                    System.out.println("FileManager:\tError: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("FileManager:\t" + e.getMessage());
        }
    }

    public synchronized void update() {
        File[] replicatedFiles = this.getReplicatedFiles();
        for (File file : replicatedFiles) {
            try {
                String fileName = file.getName();
                String replicateIPAddr = Unirest.get("/ns/files/{filename}").routeParam("filename", file.getName()).asString().getBody();
                long replicateId = Integer.parseInt(Unirest.get("/ns/files/{filename}/id").routeParam("filename", file.getName()).asString().getBody());
                if (targetIsOrigin(fileName, replicateIPAddr)) {
                    replicateId = getPrevNode(replicateId);
                    replicateIPAddr = getNodeIp(replicateId);
                }
                if (replicateId != this.node.getId()) {
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

    private int getNodeCount() {
        try {
            String nameserver = Unirest.get("/ns").asString().getBody();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(nameserver);
            return (int) ((long) json.get("Nodes"));
        } catch (Exception e) {
            return -1;
        }
    }

    private int getPrevNode(long nodeId) {
        try {
            //Request nodeId configuration from nameserver
            String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(nodeId)).asString().getBody();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(response);
            JSONObject prevNode = (JSONObject) json.get("prev");
            return (int) ((long) prevNode.get("id"));
        } catch (Exception e) {
            return -1;
        }
    }

    private String getNodeIp(long nodeId) {
        try {
            String response = Unirest.get("/ns/nodes/{nodeId}").routeParam("nodeId", String.valueOf(nodeId)).asString().getBody();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(response);
            JSONObject prevNode = (JSONObject) json.get("node");
            return (String) prevNode.get("ip");
        } catch (Exception e) {
            return "";
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
        while ((key = watchService.take()) != null && !this.isInterrupted()) {

            //sleep(50);
            for (WatchEvent<?> event : key.pollEvents()) {
                //sleep(50);
                //fileEvents.add(event.kind().toString() + " " + event.context().toString());
                System.out.println("FileManager:\t" + event.kind().toString() + " " + event.context().toString());

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
                        this.createLogFile(FileManager.logFolder + "/" + file.getName() + ".log");
                        this.updateLogFile(file.getName(), replicateId, replicateIPAddr);
                        FileTransfer.sendFile(file.getName() + ".log", logFolder, logFolder, replicateIPAddr);
                        //update sync agent:
                        this.node.getSyncAgent().addLocalFile(file.getName());
                        //[fallthrough]
                    case "ENTRY_MODIFY":
                        try {
                            System.out.println("FileManager:\tReplicating " + file.getName() + " to " + replicateIPAddr);
                            //send file to replica
                            FileTransfer.sendFile(file.getName(), localFolder, replicaFolder, replicateIPAddr);
                            System.out.println("FileManager:\tModification handled");
                        } catch (Exception e) {
                            System.out.println("FileManager:\tModification Error: " + e.getMessage() + " File:" + file.getName());
                        }
                        break;
                    case "ENTRY_DELETE":
                        try {
                            FileTransfer.deleteFile(file.getName(), replicaFolder, replicateIPAddr);
                            FileTransfer.deleteFile(file.getName() + ".log", logFolder, replicateIPAddr);
                            this.node.getSyncAgent().deleteLocalFile(file.getName());
                            System.out.println("FileManager:\tDeletion handled");
                        } catch (Exception e) {
                            System.out.println("FileManager:\tDeletion Error: " + e.getMessage() + " File:" + file.getName());
                        }
                        break;
                }
            }
            key.reset();
        }
    }

    public synchronized void shutDown() {
        String launchDirectory = System.getProperty("user.dir");
        System.out.println("FileManager:\tCurrent directory: " + launchDirectory);
        File dir = new File(launchDirectory + "/" + localFolder); //get the local folder
        //System.out.println("Directory: " + dir.getCanonicalPath()); //vieze ai zeg
        File[] files = dir.listFiles(); //get all files in the directory
        if (files == null || files.length == 0) {
            System.out.println("FileManager:\tNo files in local folder");
        } else {
            // Remove replications of local files
            for (File file : files) {
                //send fileName to NameServer
                try {
                    String deleteIPAddr = Unirest.get("/ns/files/{filename}")
                            .routeParam("filename", file.getName()).asString().getBody();
                    // if the IP addr the NS sent back is the same as the one of this node, get the prev node IP address
                    // check example 3 doc3.pdf
                    if (Objects.equals(deleteIPAddr, node.getIP())) {
                        deleteIPAddr = this.node.getPrevNodeIP();
                    }

                    System.out.println("FileManager:\tDeleting " + file.getName() + " to " + deleteIPAddr);
                    //delete file to replica
                    FileTransfer.deleteFile(file.getName(), replicaFolder, deleteIPAddr);
                    //delete log file
                    FileTransfer.deleteFile(file.getName() + ".log", logFolder, deleteIPAddr);

                    this.node.getSyncAgent().deleteLocalFile(file.getName());
                } catch (Exception e) {
                    System.out.println("FileManager:\tError: " + e.getMessage());
                }
            }
        }

        //send replica files to prev node
        dir = new File(launchDirectory + "/" + replicaFolder);
        //System.out.println("Directory: " + dir.getCanonicalPath());
        files = dir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("FileManager:\tNo files in replica folder");
            return;
        }
        for (File file : files) {
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
                } catch (Exception e) {
                    System.out.println("FileManager:\tError in parsing target node information" + target);
                    e.printStackTrace();
                }
            }
            System.out.println("FileManager:\tReplicating " + file.getName() + " to " + replicateIPAddr);

            //send file to replica
            FileTransfer.sendFile(file.getName(), replicaFolder, replicaFolder, replicateIPAddr);
            //delete file
            file.delete();
            //update log file
            updateLogFile(file.getName(), replicateId, replicateIPAddr);
            //send log file
            FileTransfer.sendFile(file.getName() + ".log", logFolder, logFolder, replicateIPAddr);
            //delete logfile
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
        try {
            File local = new File("./local");
            return local.listFiles();
        } catch (Exception e) {
            return new File[]{};
        }
    }

    public File[] getReplicatedFiles() {
        try {
            File local = new File("./replica");
            return local.listFiles();
        } catch (Exception e) {
            return new File[]{};
        }
    }

    public void updateLogFile(String fileName, long newOwner, String newIP) {
        String logFileContent = "";
        try {
            //load log file
            File logFile = new File(logFolder + "/" + fileName + ".log");

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
            //update downloads
            JSONArray downloads = (JSONArray) jsonObject.get("downloads");
            downloads.add(newOwner);
            jsonObject.put("downloads", downloads);

            //write log file
            FileWriter writer = new FileWriter(logFile);
            writer.write(jsonObject.toJSONString());
            writer.close();
        } catch (Exception e) {
            System.out.println("FileManager:\tError in updating log file\n" + logFileContent);
            e.printStackTrace();
        }
    }

    public boolean targetIsOrigin(String fileName, String targetIP) {
        try {
            //read file
            File logFile = new File(logFolder + "/" + fileName + ".log");
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            //parse json file
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
            reader.close();
            //check if target is owner
            JSONObject origin = (JSONObject) jsonObject.get("origin");
            if (origin.get("ip").equals(targetIP)) {
                System.out.println("FileManager:\tTarget is origin");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void createLogFile(String filename) throws IOException {
        //Create log file
        JSONObject owner = new JSONObject();
        owner.put("id", this.node.getId());
        owner.put("ip", this.node.getIP());
        JSONObject origin = new JSONObject();
        origin.put("id", this.node.getId());
        origin.put("ip", this.node.getIP());
        JSONArray downloads = new JSONArray();

        JSONObject logfile = new JSONObject();
        logfile.put("owner", owner);
        logfile.put("origin", origin);
        logfile.put("downloads", downloads);

        FileWriter writer = new FileWriter(filename);
        writer.write(logfile.toJSONString());
        writer.close();
    }


    public static long getOrigin(String fileName) {
        long origin = -1;
        try {
            File logFile = new File(logFolder + "/" + fileName + ".log");
            String logFileContent = "";
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            //parse json file
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
            reader.close();
            //check if target is owner
            JSONObject fileOrigin = (JSONObject) jsonObject.get("origin");
            origin = (long) fileOrigin.get("id");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return origin;
    }
}



package Node;

import Utils.Hashing;
import kong.unirest.Unirest;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.nio.file.*;
import java.util.TreeMap;

public class FileManager extends Thread {
    private Node node;
    final String localFolder = "local";
    final String replicaFolder = "replica";
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

    public void startup() {
        try {
            while (!this.node.isSetUp()) {
                Thread.sleep(100);
            }
            String launchDirectory = System.getProperty("user.dir");
            System.out.println("Current directory: " + launchDirectory);
            File dir = new File(launchDirectory + "/" + localFolder);
            System.out.println("Directory: " + dir.getCanonicalPath());
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files in local folder");
                return;
            }
            for (File file : files) {
                System.out.println("File: " + file.getName());
            }
            // Get the names of the files by using the .getName() method
            for (File file : files) {
                //System.out.println(file.getName());
                int filehash = Hashing.hash(file.getName());
                fileList.add(file.getName());
                //send fileName to NameServer
                try {
                    String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                            .routeParam("filename", file.getName()).asString().getBody();
                    // if the IP addr the NS sent back is the same as the one of this node, get the prev node IP address
                    // check example 3 doc3.pdf
                    if (Objects.equals(replicateIPAddr, node.getIP())) {
                        replicateIPAddr = this.node.getPrevNodeIP();
                    }

                    System.out.println("Replicating " + file.getName() + " to " + replicateIPAddr); //vieze ai zeg
                    //send file to replica
                    FileTransfer.sendFile(file.getName(), localFolder, replicaFolder, replicateIPAddr);

                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public void updateFileCheck(String fileName) {
        try {
            File dir = new File(localFolder);
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files in local folder");
                return;
            }
            // Get the names of the files by using the .getName() method
            // check if file is not in the list
            for (File file : files) {
                if (!fileList.contains(file.getName())) {
                    int filehash = Hashing.hash(file.getName());
                    fileList.add(file.getName());
                    //send fileName to NameServer
                    try {
                        String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                                .routeParam("filename", file.getName()).asString().getBody();
                        // if the IP addr the NS sent back is the same as the one of this node, get the prev node IP address
                        // check example 3 doc3.pdf
                        if (Objects.equals(replicateIPAddr, node.getIP())) {
                            replicateIPAddr = this.node.getPrevNodeIP();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());

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
        Socket replicateSocket = new Socket(ipAddr, 8004);

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
        path.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        WatchKey key;
        ArrayList fileEvents = new ArrayList();
        while ((key = watchService.take()) != null) {

            //sleep(50);
            for (WatchEvent<?> event : key.pollEvents()) {
                //sleep(50);
                //fileEvents.add(event.kind().toString() + " " + event.context().toString());

                switch (event.kind().toString()) {
                    case "ENTRY_CREATE":

                        break;

                    case "ENTRY_DELETE":
                        break;

                    case "ENTRY_MODIFY":
                        File file = new File(event.context().toString());
                        int filehash = Hashing.hash(file.getName());
                        try {
                            String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                                    .routeParam("filename", file.getName()).asString().getBody();
                            // if the IP addr the NS sent back is the same as the one of this node, get the prev node IP address
                            // check example 3 doc3.pdf
                            if (Objects.equals(replicateIPAddr, node.getIP())) {
                                replicateIPAddr = this.node.getPrevNodeIP();
                            }

                            System.out.println("Replicating " + file.getName() + " to " + replicateIPAddr); //vieze ai zeg
                            //send file to replica
                            FileTransfer.sendFile(file.getName(),localFolder , replicaFolder, replicateIPAddr);
                            System.out.println("Modification handled");
                        } catch (Exception e) {
                            System.out.println("Modification Error: " + e.getMessage() + " File:" + file.getName());
                        }
                        break;
                }

                System.out.println(
                        "Event kind:" + event.kind()
                                + ". File affected: " + event.context() + ".");

            }

            key.reset();
        }
    }

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
}



package Node;

import Utils.Hashing;
import kong.unirest.Unirest;

import java.io.*;
import java.net.Socket;
import java.util.Objects;
import java.util.TreeMap;

public class FileManager extends Thread {
    private Node node;
    final String localFolder= "/local";
    final String replicaFolder = "/replica";
    //private final TreeMap<Integer,String> fileMapping = new TreeMap<>();

    private static final int SENDING_PORT = 8004;


    public FileManager(Node node) {
        super("FileManager");
        this.node = node;
        this.setDaemon(true);
        this.startup();
        this.start();
    }

    public void startup() {
        try {
            File dir = new File(localFolder);
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files in local folder");
                return;
            }
            // Get the names of the files by using the .getName() method
            for (File file : files) {
                //System.out.println(file.getName());
                int filehash = Hashing.hash(file.getName());
                //fileMapping.put(filehash,file.getName());
                //send fileName to NameServer
                try {
                    String replicateIPAddr = Unirest.get("/ns/files/{filename}")
                            .routeParam("filename", file.getName()).asString().getBody();
                    // if the IP addr the NS sent back is the same as the one of this node, get the prev node IP address
                    if (Objects.equals(replicateIPAddr, node.getIP())) {
                        replicateIPAddr = this.node.getPrevNodeIP();

                    }
                    System.out.println("Replicating " + file.getName() + " to " + replicateIPAddr); //vieze ai zeg

                    //startReplication(file, replicateIPAddr);
                }
                catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        }
        catch (Exception e) {
                System.err.println(e.getMessage());
            }
    }

    /**
     * Sends a given file to a given IP address.
     * @param file
     * @param ipAddr
     * @throws IOException
     */
    public void startReplication(File file, String ipAddr) throws IOException {
        //Socket replicateSocket = new Socket(ipAddr, 8004);

        //FileInputStream fis = null;
        //BufferedInputStream bis = null;
        //OutputStream os = null;

        //try {
            //byte[] buffer = new byte[(int) file.length()];
            //fis = new FileInputStream(file);
            //bis = new BufferedInputStream(fis);
            //bis.read(buffer, 0, buffer.length);
            //os = replicateSocket.getOutputStream();

            //System.out.println("Sending " + file.getName() + "(" + buffer.length + " bytes)");
            //long time = System.currentTimeMillis();
            //os.write(buffer, 0, buffer.length);
            //os.flush();
            //System.out.println("Request processed: " + time);
       // }
        //finally {
            //if (bis != null) bis.close();
            //if (os != null) os.close();
            //if (replicateSocket != null) replicateSocket.close();
        //}
    }
}



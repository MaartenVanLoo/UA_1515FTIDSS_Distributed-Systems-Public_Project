package Node;

import Utils.Hashing;

import java.io.File;
import java.util.TreeMap;

public class FileManager extends Thread {
    final String localFolder= "/local";
    final String replicaFolder = "/replica";
    private final TreeMap<Integer,String> fileMapping = new TreeMap<>();


    public FileManager() {
        super("FileManager");
        this.setDaemon(true);
        this.startup();
        this.start();
    }
    public void startup() {
        try {
            File dir = new File("localFolder");
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files in local folder");
                return;
            }
            // Get the names of the files by using the .getName() method
            for (File file : files) {
                //System.out.println(file.getName());
                int filehash = Hashing.hash(file.getName());
                fileMapping.put(filehash,file.getName());
                //send fileHash to NameServer

            }
        }
        catch (Exception e) {
                System.err.println(e.getMessage());
            }
    }
}



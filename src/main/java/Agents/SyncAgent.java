package Agents;

import java.io.IOException;
import java.util.ArrayList;
import Node.*;
import java.io.File;

public class SyncAgent {
    // go over every node and make a list of all files in localfolder
    public void getLocalFiles(Node node) {
        ArrayList<String> localFiles = new ArrayList<>();
        ;

        try {
            while (!this.node.isSetUp()) {
                Thread.sleep(100); // wait for the node to be set up
            }
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
            // Get the names of the files by using the .getName() method
            for (File file : files) {
                //System.out.println(file.getName());
                //int filehash = Hashing.hash(file.getName()); //  nameserver doet dit
                localFiles.add(file.getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

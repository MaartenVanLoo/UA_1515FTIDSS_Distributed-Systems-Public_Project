package Agents;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import Node.*;
import java.io.File;
import jade.core.*;
import jade.core.behaviours.*;
import javassist.Loader;

import javax.sound.midi.Receiver;

public class SyncAgent extends Agent {

    private Node.Node node;
    private String nextNodeIP;
    private long nextNodeId;
    private ArrayList<String> Files;

    public SyncAgent(Node.Node node) {
        this.setup(node);
    }

    public void setup(Node.Node node) {
        this.node = node;
        this.nextNodeIP = node.getNextNodeIP();
        this.nextNodeId = node.getNextNodeId();
        addBehaviour(new BehaviourSync(this));//adds behaviour after which it runs.
        this.Files =
    }

    public Node.Node getNode(){
        return this.node;
    }

    private class BehaviourSync extends CyclicBehaviour {

        private String localFolder = "local";
        private Node.Node node;
        private ArrayList<String> localFiles;
        private BehaviourSync(Agent a) {
            super(a);
            this.node = ((SyncAgent) a).getNode();
            this.action();
        }

        public void action() { //first run createLocalList to get local files, then get the files from the next node
            createLocalList();
        }
        public void createLocalList(){
            try {
                while (!this.node.isSetUp()) {
                    Thread.sleep(100); // wait for the node to be set up
                }
                localFiles = new ArrayList<String>();
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

    private class LockBehaviour extends SimpleBehaviour {

        private ReceiverBehaviour receiver;
        private boolean finished;
        private LockBehaviour(Agent a) {
            super(a);
            finished = false;
        }

        public void action() {
            // TODO Auto-generated method stub
        receiver = new ReceiverBehaviour(myAgent, -1, null);//waits untill any message arrives
            if(receiver.done()){
                try{

                }catch(Exception e){
                    e.printStackTrace();
                }
            }

        }

        @Override
        public boolean done() {
            finished = true;
            return finished;
        }


    }

    private class UnlockBehaviour extends SimpleBehaviour {
        private boolean finished;
        private UnlockBehaviour(Agent a) {
            super(a);
        }

        public void action() {
            // TODO Auto-generated method stub

        }

        @Override
        public boolean done() {
            finished = true;
            return finished;
        }

    }

}

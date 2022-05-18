package Agents;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import Node.*;
import java.io.File;
import jade.core.*;
import jade.core.behaviours.*;
import jade.domain.introspection.ACLMessage;
import javassist.Loader;

import javax.sound.midi.Receiver;

public class SyncAgent extends Agent {

    private Node.Node node;
    private String nextNodeIP;
    private long nextNodeId;
    private ArrayList<String> files;

    public ArrayList<String> getFiles() {
        return files;
    }
    public SyncAgent(Node.Node node) {
        this.setup(node);
    }

    public void setup(Node.Node node) {
        this.node = node;
        this.nextNodeIP = node.getNextNodeIP();
        this.nextNodeId = node.getNextNodeId();
        addBehaviour(new LocalBehaviour(this));//adds behaviour after which it runs.
        addBehaviour(new SyncBehaviour(this));

    }

    public Node.Node getNode(){
        return this.node;
    }

    private class LocalBehaviour extends SimpleBehaviour { //create list of local files, only has to be executed once hence simple behaviour

        private String localFolder = "local";
        private Node.Node node;
        private SyncAgent agent;
        private boolean finished = false;

        private LocalBehaviour(Agent a) {
            super(a);
            this.node = ((SyncAgent) a).getNode();
            this.action();
            this.agent = (SyncAgent) a;
        }

        public void action() { //first run createLocalList to get local files, then get the files from the next node
            createLocalList();
            done();
        }

        @Override
        public boolean done() {
            System.out.println("Done");
            return finished;
        }

        public void createLocalList(){
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
                    this.agent.getFiles().add(file.getName());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finished = true;
        }
    }

    private class SyncBehaviour extends CyclicBehaviour{ //synchronize local files with the next node

        private Node.Node node;
        private SyncAgent agent;


        private SyncBehaviour(Agent a) {
            super(a);
            this.node = ((SyncAgent) a).getNode();
            this.action();
            this.agent = (SyncAgent) a;
        }

        public void action() {
            //TODO: we need to add a whole communication protocol here (queries, answers, etc)
            if(this.agent.getFiles().size() > 0){
                System.out.println("Sending file list to next node");
                //sendFiles();
            }
            else{
                System.out.println("No files to send");
            }
            jade.lang.acl.ACLMessage msg = receive();
            if(msg != null){
                if(msg.getPerformative() == ACLMessage.QUERY_IF){
                    System.out.println("Received query");
                    //...
                }
                else if(msg.getPerformative() == ACLMessage.INFORM){
                    //....
                }else{//it's hopefully the data...
                    System.out.println("Message received: "+msg.getContent());
                    agent.getFiles().add(msg.getContent());//adds received list to our list.
                    System.out.println("Added to list");
                }
            }

        }
       public void sendFiles(){
            AID dest = new AID();//Agent ID of the next node
            //dest = ...
            ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
            msg.addReceiver(new AID(nextNodeId, AID.ISLOCALNAME));
            msg.setLanguage("English");
            msg.setOntology("file-transfer");
            msg.setProtocol("fipa-request");
            msg.setContent("send-files");
            this.agent.send(msg);
            System.out.println("Sent message");
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

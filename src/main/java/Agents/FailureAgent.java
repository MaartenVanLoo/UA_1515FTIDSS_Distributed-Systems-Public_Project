package Agents;

import Node.Node;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import NameServer.*;

import java.io.Serializable;


//vergeef de pseudocode wat
public class FailureAgent implements Runnable, Serializable {

    private Node node;
    String starterNodeIP = this.node.getIP();

    FailureAgent(Node node, Node thisNode) {
        this.node = thisNode;
        Node failingNode = node;
       String starterNodeIP = this.node.getIP();
        // get the file list of this node
        thisNode.getFileList();

    }

    //get failing node from Nameserver via REST
    public void setup() {

    }
    private class behaviour extends Behaviour {
        @Override
        public void action() {

        }

        @Override
        public boolean done() {
            return false;
        }
    }

    @Override
    public void run() {
        try {

            //send Agent to next node
            String Address = node.getNextNodeIP();
            // REST call to start failure on next node
            // chack if the next node is the starter node
            // if it is starter node, terminate agent
            // else, send agent to next node
            if(node.getNextNodeIP().equals(starterNodeIP)){
                //terminate agent

                System.out.println("Failure Agent: " + node.getIP() + " has completed ring and has terminated");
                Agent.stop();
            }




        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

package Agents;

import Node.Node;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import NameServer.*;
import jade.core.behaviours.OneShotBehaviour;
import kong.unirest.Unirest;


//sorry voor halve pseudocode
public class FailureAgent extends Agent {
    private static final long serialVersionUID = 1L;

    private Node node;
    String starterNodeIP;

    {
        assert false;
        starterNodeIP = this.node.getIP();
    }


    public FailureAgent(Node node) {
        this.node = node;
        setup();
    }

    public void setup() {
        addBehaviour(new Behaviour(this) {
            private static final long serialVersionUID = 1L;
            @Override
            public void action() {
                getList();
                if (done()) {
                    System.out.println("Failure Agent: " + node.getIP() + " has completed ring and has terminated");
                    doDelete();
                } else {
                    //send agent to next node via REST
                    System.out.println("Failure Agent: " + node.getIP() + " has NOT completed ring and has sent to next node");
                    Unirest.post("node/agent");

                }
            }
            @Override
            public boolean done() {
                if (node.getIP().equals(starterNodeIP)) {
                    return true;
                }
                else {
                    return false;
                }
            }
        });

    }
    
    public void getList() {
        //get list of files in the
    }
}

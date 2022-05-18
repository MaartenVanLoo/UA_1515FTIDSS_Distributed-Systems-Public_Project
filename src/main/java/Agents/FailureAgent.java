package Agents;

import Node.Node;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import NameServer.*;
import jade.core.behaviours.OneShotBehaviour;


//sorry voor halve pseudocode
public class FailureAgent extends Agent {
    private static final long serialVersionUID = 1L;

    private Node node;
    String starterNodeIP = this.node.getIP();


    public FailureAgent(Node node) {
        this.node = node;
        setup();
    }

    //get failing node from Nameserver via REST
    public void setup() {
        addBehaviour(new Behaviour() {
            private static final long serialVersionUID = 1L;
            @Override
            public void action() {
                walk();

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


    public void walk() {
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
                Behaviour.done();
            }




        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

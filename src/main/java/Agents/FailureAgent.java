/* package Agents;

import Node.Node;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import NameServer.*;
import jade.core.behaviours.OneShotBehaviour;


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
                fly();

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
    
    public void fly() {
        try {
            //send Agent to next node
            // check if the next node is the starter node
            // if it is starter node, terminate agent
            // else, send agent to next node
            if(node.getNextNodeIP().equals(starterNodeIP)){
                //terminate agent

                System.out.println("Failure Agent: " + node.getIP() + " has completed ring and has terminated");
                doDelete();
            } else
            {
                //send agent to next node
                System.out.println("Failure Agent: " + node.getIP() + " has NOT completed ring and has sent to next node");
                doMove(node.getNextNodeIP());

            }




        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
*/
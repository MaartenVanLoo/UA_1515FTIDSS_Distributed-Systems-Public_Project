package Agents;

import Node.Node;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import NameServer.*;

import java.io.Serializable;

public class FailureAgent implements Runnable, Serializable {

    private Node node;

    FailureAgent(Node node, Node thisNode) {
        this.node = thisNode;
        Node failingNode = node;
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
            //get fileList of current node


        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}

package Client;
import NameServer.NameServer;

import java.io.*;
import java.net.*;
import java.util.TreeMap;

public class Client {
    public static void main(String[] args) {
        NameServer ns = new NameServer();
        ns.getIdMap().clear();
        ns.addNode(ns.hash("node0"),"0");
        ns.addNode(ns.hash("node1"),"1");
        ns.addNode(ns.hash("node2"),"2");
        ns.addNode(ns.hash("node3"),"3");
        ns.addNode(ns.hash("node4"),"4");
        ns.addNode(ns.hash("node5"),"5");
        ns.addNode(ns.hash("node6"),"6");
        ns.addNode(ns.hash("node7"),"7");
        ns.addNode(ns.hash("node8"),"8");
        ns.addNode(ns.hash("node9"),"9");

        TreeMap<Integer,String> idmap =  ns.getIdMap();

        //print the id map in order
        for (Integer key : idmap.keySet()) {
            System.out.println(key + " " + idmap.get(key));
        }

    }

}

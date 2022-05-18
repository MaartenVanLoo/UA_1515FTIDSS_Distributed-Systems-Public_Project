package Agents;

import Node.Node;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import NameServer.*;
import jade.core.behaviours.OneShotBehaviour;
import kong.unirest.Unirest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Serializable;
import java.util.stream.Collectors;

import Node.FileManager;

public class FailureAgent implements Runnable, Serializable {
    private static final long serialVersionUID = 1L;

    private Node node;
    private Node failureNode;

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
            //get list of log files in the list from current node
            //if failing node is owner of the file replicate elsewhere
            try {
                File dir = new File(FileManager.logFolder);
                File[] files = dir.listFiles();
                for (File file : files) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    //parse json file
                    JSONParser parser = new JSONParser();
                    JSONObject jsonObject = (JSONObject) parser.parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
                    reader.close();
                    //check if target is owner
                    JSONObject origin = (JSONObject) jsonObject.get("origin");

                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    @Override
    public void run() {
        setup();
    }
}

package Agents;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import Node.*;
import java.io.File;
import java.util.concurrent.Executor;

import com.sun.net.httpserver.HttpServer;
import jade.core.*;
import jade.core.behaviours.*;
import jade.domain.introspection.ACLMessage;
import javassist.Loader;
import kong.unirest.Unirest;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import javax.sound.midi.Receiver;

public class SyncAgent extends Thread {

    private Node.Node node;
    private String nextNodeIP;
    private long nextNodeId;
    private ArrayList<String> files;

    private HttpServer server;
    private static final int HTTP_PORT = 8082;

    private volatile boolean running = false;

    public SyncAgent(Node.Node node) {
        this.setDaemon(true);
        this.node = node;
        this.nextNodeIP = node.getNextNodeIP();
        this.nextNodeId = node.getNextNodeId();
        this.files = new ArrayList<>();

        //setup websocket
        try {
            this.server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            this.server.createContext("/fileList", (exchange) -> {
                if (exchange.getRequestMethod().equals("GET")) {
                    //send file list in body
                    JSONArray jsonArray = new JSONArray();
                    for (String file: this.files){
                        jsonArray.add(file);
                    }
                    exchange.getResponseBody().write(jsonArray.toJSONString().getBytes());
                    exchange.sendResponseHeaders(200, 0);
                }
                else{
                    exchange.sendResponseHeaders(501, -1);
                }
                exchange.close();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        //start thread
        this.start();
    }

    @Override
    public void run() {
        running = true;
        this.makeLocalList();
        this.server.start();
        while (running){
            try {
                getNeighbourList();
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.server.stop(0);
    }

    public void makeLocalList(){//make a list out of local files.
        try {
            File dir = new File(FileManager.localFolder);
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files in local folder");
                return;
            }
            for (File file: files){
                this.files.add(file.getName());
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public ArrayList<String> getFileList(){
        return this.files;
    }

    public void addLocalFile(String filename){//update the local list when a new local file get's added.
        if(!this.files.contains(filename)){
            this.files.add(filename);
        }
        else{
            System.out.println("File duplicate!!");//enkel debug..
        }
    }

    public void getNeighbourList(){
        JSONParser parser = new JSONParser();
        try {
            JSONArray neighbourFiles = (JSONArray) parser.parse(Unirest.get("http://" + this.nextNodeIP + ":8082/fileList").asString().getBody());
            for (Object file : neighbourFiles) {
                if (!this.files.contains(file)) {
                    this.files.add((String) file);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }









}

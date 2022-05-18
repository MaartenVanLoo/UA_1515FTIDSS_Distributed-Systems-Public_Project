package Agents;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import Node.*;
import java.io.File;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import kong.unirest.Unirest;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class SyncAgent extends Thread {

    private Node node;
    private String nextNodeIP;
    private long nextNodeId;
    private ArrayList<String> files;

    private HttpServer server;
    private static final int HTTP_PORT = 8082;

    private volatile boolean running = false;

    public SyncAgent(Node node) {
        this.setDaemon(true);
        this.node = node;
        this.nextNodeIP = node.getNextNodeIP();
        this.nextNodeId = node.getNextNodeId();
        this.files = new ArrayList<>();

        //setup websocket
        try {
            this.server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            this.server.createContext("/fileList", (exchange) -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (exchange.getRequestMethod().equals("GET")) {
                    //send file list in body
                    JSONObject json = new JSONObject();
                    JSONArray jsonArray = new JSONArray();
                    for (String file: this.files){
                        jsonArray.add(file);
                    }
                    json.put("fileList", jsonArray);
                    String response = json.toJSONString();
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream outputStream = exchange.getResponseBody();
                    outputStream.write(response.getBytes());
                    outputStream.flush();
                    outputStream.close();
                }
                else if (exchange.getRequestMethod().equals("DELETE")){
                    //delete file from files
                    System.out.println("DELETE");
                    System.out.println(exchange.getRequestURI());
                    String fileName = exchange.getRequestURI().getQuery().replace("/fileList","");
                    if (this.files.contains(fileName)){
                        this.files.remove(fileName);
                        System.out.println("Notify neighbours of deletion of file " + fileName);
                        Unirest.delete("http://" + this.node.getNextNodeIP() + ":8082/fileList/" + fileName).asString();
                        System.out.println("notified");
                    }
                    exchange.sendResponseHeaders(200, -1);
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
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.start();
        while (running){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!this.node.isSetUp()){
                continue;
            }

            getNeighbourList();

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

    public void deleteLocalFile(String filename){
        if (!this.files.contains(filename)){
            System.out.println("File not found!!");//enkel debug..
            return;
        }
        this.files.remove(filename);
        System.out.println("Notify neighbours file deleted");
        Unirest.delete("http://" + this.node.getNextNodeIP() + ":8082/fileList/" + filename).asString();
        System.out.println("Notification done");
    }

    public void getNeighbourList(){
        JSONParser parser = new JSONParser();
        try {
            JSONObject neighbourFiles = (JSONObject) parser.parse(Unirest.get("http://" + this.node.getNextNodeIP() + ":8082/fileList").asString().getBody());
            JSONArray fileList =  (JSONArray)neighbourFiles.get("fileList");
            for (Object file : fileList) {
                if (!this.files.contains((String)file)) {
                    this.files.add((String) file);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }









}

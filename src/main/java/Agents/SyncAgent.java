package Agents;

import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import Node.*;
import java.io.File;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sun.net.httpserver.HttpServer;

import kong.unirest.Unirest;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;


public class SyncAgent extends Thread {

    private Node node;
    private ArrayList<String> files;
    private volatile HashMap<String, Boolean> fileLocks;
    private volatile HashMap<String, String> lockOwner;
    private ReadWriteLock fileMapLock = new ReentrantReadWriteLock();

    private HttpServer server;
    private static final int syncAgentPort = 8082;

    private MulticastSocket multicastSocket;
    private static final String multicastIP ="224.0.0.100"; //https://en.wikipedia.org/wiki/Multicast_address
    private InetAddress group;
    private MulticastListener multicastListener;


    private volatile boolean running = false;

    public SyncAgent(Node node) {
        this.setDaemon(true);
        this.node = node;
        this.files = new ArrayList<>();
        this.fileLocks = new HashMap<>();
        this.lockOwner = new HashMap<>();

        //setup websocket
        try {
            this.server = HttpServer.create(new InetSocketAddress(syncAgentPort), 0); //TCP
            this.server.setExecutor(Executors.newCachedThreadPool());
            this.server.createContext("/fileList", (exchange) -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (exchange.getRequestMethod().equals("GET")) {
                    //send file list in body
                    JSONObject json = new JSONObject();
                    JSONArray fileList = new JSONArray();
                    for (String file: this.files){
                        fileList.add(file);
                    }
                    JSONArray lockList = new JSONArray();
                    this.fileMapLock.readLock().lock();
                    for (String file: this.fileLocks.keySet()){
                        JSONObject lock = new JSONObject();
                        lock.put("file", file);
                        lock.put("lock", this.fileLocks.get(file));
                        lock.put("owner", this.lockOwner.get(file));
                        lockList.add(lock);
                    }
                    this.fileMapLock.readLock().unlock();

                    json.put("fileList", fileList);
                    json.put("lockList", lockList);
                    String response = json.toJSONString();
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream outputStream = exchange.getResponseBody();
                    outputStream.write(response.getBytes());
                    outputStream.flush();
                    outputStream.close();
                }
                else if (exchange.getRequestMethod().equals("DELETE")){
                    try {
                        //delete file from files
                        System.out.println("SyncAgent:\tDELETE request");
                        String fileName = exchange.getRequestURI().toString().replace("/fileList/", "");
                        System.out.println("SyncAgent:\tfile to delete: " + fileName);
                        //need to consume all the data from the input stream before closing it (see  https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/HttpExchange.html);
                        exchange.getRequestBody().readAllBytes();
                        exchange.getRequestBody().close();

                        exchange.sendResponseHeaders(200, -1);
                        exchange.close(); //close before opening connection to next node, otherwise all connections will be waiting till the last one closes!
                        try {
                            if (this.files.contains(fileName)) {
                                this.files.remove(fileName);
                                System.out.println("SyncAgent:\tNotify neighbours of deletion of file " + fileName);
                                System.out.println("SyncAgent:\tNotifying :" + this.node.getNextNodeIP());
                                Unirest.delete("http://" + this.node.getNextNodeIP() + ":8082/fileList/" + fileName).asString();
                                System.out.println("SyncAgent:\tNotified neighbour");
                            }
                        }catch (Exception ignored){}
                        return;
                    }catch(Exception e){
                        exchange.sendResponseHeaders(404,-1);
                        exchange.close();
                        e.printStackTrace();
                        return;
                    }
                }
                else{
                    exchange.sendResponseHeaders(501, -1);
                }
                exchange.close();
            });
            this.server.setExecutor(Executors.newCachedThreadPool());
        } catch (IOException e) {
            e.printStackTrace();
        }

        //setup multicastSocket
        try {
            this.group = InetAddress.getByName(multicastIP);
            this.multicastSocket = new MulticastSocket(syncAgentPort); //UDP
            this.multicastSocket.setLoopbackMode(false); //Do loopback! (true to disable but lock and unlock methods below are based on loopback to be enabled)
            this.multicastSocket.joinGroup(this.group);
            multicastListener = new MulticastListener();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //start thread
        this.start();
    }

    public HashMap<String, Boolean> getFileLocks() {
        return fileLocks;
    }

    public HashMap<String, String> getLockOwner() {
        return lockOwner;
    }

    @Override
    public void run() {
        running = true;
        this.makeLocalList();
        if (this.server != null) this.server.start();
        if (this.multicastListener != null) this.multicastListener.start();

        while (running){
            if (this.node.isSetUp()){
                getNeighbourList();
            }
            try {
                long sleep = 10000 + (long)((Math.random()-0.5) * 2000);
                Thread.sleep(this.node.isSetUp()?sleep:100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        this.server.stop(0);
        this.multicastListener.interrupt();
    }

    public void makeLocalList(){//make a list out of local files.
        try {
            File dir = new File(FileManager.localFolder);
            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("SyncAgent:\tNo files in local folder");
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
            System.out.println("SyncAgent:\tFile duplicate!!");//enkel debug..
        }
    }

    public void deleteLocalFile(String filename){
        if (!this.files.contains(filename)){
            System.out.println("SyncAgent:\tFile not found!!");//enkel debug..
            return;
        }
        // remove lock if exists (check done inside unlock function);
        // unlock before removing file from file list!
        this.unlockFile(filename);

        this.files.remove(filename);
        System.out.println("SyncAgent:\tNotify neighbours " + filename +  " is deleted");
        System.out.println("SyncAgent:\tNotifying :" + this.node.getNextNodeIP());
        int status = Unirest.delete("http://" + this.node.getNextNodeIP() + ":8082/fileList/" + filename).asString().getStatus();
        System.out.println("SyncAgent:\tDelete status: " + status);
        System.out.println("SyncAgent:\tNotification done");



    }

    public void getNeighbourList(){
        JSONParser parser = new JSONParser();
        try {
            JSONObject neighbourFiles = (JSONObject) parser.parse(Unirest.get("http://" + this.node.getNextNodeIP() + ":8082/fileList").asString().getBody());
            JSONArray fileList =  (JSONArray)neighbourFiles.get("fileList");
            JSONArray lockList =  (JSONArray)neighbourFiles.get("lockList");

            //sync files
            for (Object file : fileList) {
                if (!this.files.contains((String)file)) {
                    this.files.add((String) file);
                }
            }
            //sync locks
            this.fileMapLock.writeLock().lock();
            for (Object lock : lockList) {
                try {
                    JSONObject lockObj = (JSONObject) lock;
                    String filename = (String) lockObj.get("file"); if (filename == null) continue;
                    String owner = (String) lockObj.get("owner");
                    Boolean isLocked = (Boolean) lockObj.get("lock");
                    if (!this.fileLocks.containsKey(filename)) {
                        this.fileLocks.put(filename, isLocked);
                    }
                    if (!this.lockOwner.containsKey(filename)) {
                        this.lockOwner.put(filename, owner);
                    }
                }catch (Exception ignore) {}
            }
            this.fileMapLock.writeLock().unlock();
        }catch(Exception ignore){}
    }


    public synchronized boolean lockFile(String fileName){
        if (this.multicastSocket == null) return false;
        fileMapLock.writeLock().lock();
        if (this.fileLocks.containsKey(fileName)) {
            if (this.fileLocks.get(fileName)) {
                fileMapLock.writeLock().unlock();
                return false;
            }
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileName",fileName);
        jsonObject.put("name",this.node.getName());
        jsonObject.put("action","lock");

        String message = jsonObject.toJSONString();
        DatagramPacket packet = new DatagramPacket(message.getBytes(StandardCharsets.UTF_8),message.length(), this.group,SyncAgent.syncAgentPort);
        try {
            this.multicastSocket.send(packet);
        }catch(Exception e){
            e.printStackTrace();
        }

        // Make sure the file is unlocked! => don't return from this function until the file is unlocked.
        // Therefore, don't count on loopback of multicast socket

        fileLocks.put(fileName,true);
        lockOwner.put(fileName,this.node.getName());

        fileMapLock.writeLock().unlock();
        return true;
    }

    public synchronized boolean unlockFile(String fileName){
        if (this.multicastSocket == null) return false;
        fileMapLock.writeLock().lock();
        if (!this.fileLocks.containsKey(fileName)) {fileMapLock.writeLock().unlock(); return true;} //after this line we know the key fileName exists, no nullptr exceptions with get
        if (!this.fileLocks.get(fileName)) {fileMapLock.writeLock().unlock(); return true;} //don't unlock a file that isn't locked

        System.out.println("unlock file"+ fileName);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileName",fileName);
        jsonObject.put("name",this.node.getName());
        jsonObject.put("action","unlock");

        String message = jsonObject.toJSONString();
        DatagramPacket packet = new DatagramPacket(message.getBytes(StandardCharsets.UTF_8),message.length(), this.group,SyncAgent.syncAgentPort);
        try {
            this.multicastSocket.send(packet);
        }catch(Exception e){
            e.printStackTrace();
        }

        // Make sure the file is unlocked! => don't return from this function until the file is unlocked.
        // Therefore, don't count on loopback of multicast socket
        this.fileLocks.remove(fileName);
        this.lockOwner.remove(fileName);

        fileMapLock.writeLock().unlock();
        return true;
    }

    private class MulticastListener extends Thread{
        private byte[] buf = new byte[256];

        public MulticastListener() {}

        @Override
        public void run() {
            try {
                SyncAgent.this.multicastSocket.setSoTimeout(1000);
            } catch (SocketException e) {
                e.printStackTrace();
                System.out.println("SyncAgent:\tFailed to set timeout in SyncAgent");
                return;
            }
            while (SyncAgent.this.running){
                try{
                    DatagramPacket packet = new DatagramPacket(buf,buf.length);
                    SyncAgent.this.multicastSocket.receive(packet);
                    String received  = new String(packet.getData(),0,packet.getLength());

                    //parse received data (should be JSON format)
                    JSONParser parser = new JSONParser();
                    JSONObject data = (JSONObject) parser.parse(received);

                    String fileName = (String)data.get("fileName");
                    String nodeName = (String)data.get("name");
                    String action = (String)data.get("action");

                    if (fileName == null) {
                        System.out.println("SyncAgent:\tReceived packet with no fileName");
                        continue;
                    }
                    // do specified action
                    SyncAgent.this.fileMapLock.writeLock().lock();
                    if (action.equals("lock")){
                        fileLocks.put(fileName,true);
                        lockOwner.put(fileName,nodeName);
                        System.out.println("SyncAgent:\tLocked file " + fileName + " by " + nodeName);
                    }else if(action.equals("unlock")){
                        fileLocks.put(fileName,false);  // make sure the entry exists!
                        lockOwner.put(fileName,"");     // make sure the entry exists!
                        fileLocks.remove(fileName);
                        lockOwner.remove(fileName);
                        System.out.println("SyncAgent:\tUnlocked file " + fileName);
                    }else{
                        System.out.println("SyncAgent:\tReceived bad lock request");
                    }
                    SyncAgent.this.fileMapLock.writeLock().unlock();
                }
                catch(java.net.SocketTimeoutException ignore){}
                catch(Exception e){
                    e.printStackTrace();
                }

            }
            try {
                SyncAgent.this.multicastSocket.leaveGroup(SyncAgent.this.group);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}

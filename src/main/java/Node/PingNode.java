package Node;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class PingNode extends Thread{
    private Node node;
    private int nextUnanswered = 0;
    private int prevUnanswered = 0;
    private int delay = 5000;
    private boolean running = false;

    public PingNode(Node node){
        this.setDaemon(true); //make sure the thread dies when the main thread dies
        this.node = node;
    }

    public void nextAnswered() {
        this.nextUnanswered--;
    }
    public void prevAnswered() {
        this.prevUnanswered--;
    }
    public void resetNext(){
        this.nextUnanswered = 0;
    }
    public void resetPrev(){
        this.prevUnanswered = 0;
    }
    public void shutdown(){
        this.running = false;
    }
    public void run(){
        this.running = true;
        while(this.running && !this.isInterrupted()){
            //sleep 5 seconds
            try{
                Thread.sleep(delay);
            }catch(InterruptedException e){
                this.running = false;
                return;
            }
            if (!this.node.isSetUp()) continue; // wait till node is set up
            InetAddress nextAddress;
            InetAddress prevAddress;
            try {
                nextAddress = InetAddress.getByName(this.node.getNextNodeIP());
                System.out.println("PingNode: next address: " + this.node.getNextNodeIP());
                prevAddress = InetAddress.getByName(this.node.getPrevNodeIP());
                System.out.println("PingNode: prev address: " + this.node.getPrevNodeIP());
            } catch (UnknownHostException e) {
                System.out.println("Unable to find neighbor ips");
                this.node.printStatus();
                continue;
            }
            String ping = "{\"type\":\"Ping\"," +
                    "       \"nodeId\":" + this.node.getId() + "}";

            //ping neighbors
            DatagramPacket pingNext = new DatagramPacket(ping.getBytes(StandardCharsets.UTF_8), ping.length(),nextAddress , 8001);
            DatagramPacket pingPrev = new DatagramPacket(ping.getBytes(StandardCharsets.UTF_8), ping.length(),prevAddress , 8001);

            try {
                this.node.getListeningSocket().send(pingNext);
                this.nextUnanswered++;
                this.node.getListeningSocket().send(pingPrev);
                this.prevUnanswered++;
            } catch (IOException e) {
                //TODO: failure detection?
                if (this.node.getListeningSocket() != null && this.node.getListeningSocket().isClosed()){
                    //socket closed => node is dead
                    this.running = false;
                    break;
                }
                e.printStackTrace();
            }
            if (this.nextUnanswered >3) {
                this.node.failureHandler((int)this.node.getNextNodeId());
            }
            if (this.prevUnanswered >3) {
                this.node.failureHandler((int)this.node.getPrevNodeId());
            }
        }
    }
}


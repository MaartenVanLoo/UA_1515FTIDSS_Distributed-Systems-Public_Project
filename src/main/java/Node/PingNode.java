package Node;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class PingNode extends Thread{
    private Node node;
    private int nextUnanswerd = 0;
    private int prevUnanswerd = 0;
    private int delay = 5000;
    private boolean running = false;

    public PingNode(Node node){
        this.setDaemon(true); //make sure the thread dies when the main thread dies
        this.node = node;

    }

    public void nextAnswered() {
        this.nextUnanswerd --;
    }
    public void prevAnswered() {
        this.prevUnanswerd --;
    }
    public void resetNext(){
        this.nextUnanswerd = 0;
    }
    public void resetPrev(){
        this.prevUnanswerd = 0;
    }
    public void shutdown(){
        this.running = false;
    }
    public void run(){
        this.running = true;
        while(this.running){
            //sleep 5 seconds
            try{
                Thread.sleep(delay);
            }catch(InterruptedException e){
                this.running = false;
                return;
            }

            InetAddress nextAddress;
            InetAddress prevAddress;
            try {
                nextAddress = InetAddress.getByName(this.node.getNextNodeIP());
                prevAddress = InetAddress.getByName(this.node.getPrevNodeIP());
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
                this.nextUnanswerd++;
                this.node.getListeningSocket().send(pingPrev);
                this.prevUnanswerd++;
            } catch (IOException e) {
                //TODO: failure detection?
                e.printStackTrace();
            }
            if (this.nextUnanswerd >3) {
                this.node.failureHandler((int)this.node.getNextNodeId());
            }
            if (this.prevUnanswerd >3) {
                this.node.failureHandler((int)this.node.getPrevNodeId());
            }
        }
    }
}


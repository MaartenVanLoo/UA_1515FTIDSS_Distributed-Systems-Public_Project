package NameServer;

import java.io.*;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.json.simple.*;
import org.json.simple.parser.*;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

public class NameServer {
    private final String mappingFile = "rsc/nameServerMap";
    private final HashMap<Integer,String> ipMapping = new HashMap<>(); //id =>ip;
    public NameServer() throws IOException {
        //init
        try {
            readMapFromFile(this.mappingFile);
        }catch (IOException e){
            System.out.println("File reading error:" + e.getMessage());
            System.out.println("Creating new file.");
            this.ipMapping.clear();
            writeMapToFile(this.mappingFile);
            System.out.println("Starting with empty map.");
        }
        catch(ParseException e){
            System.out.println("File parsing error:" + e.getMessage());
            System.out.println("Creating new file.");
            this.ipMapping.clear();
            writeMapToFile(this.mappingFile);
            System.out.println("Starting with empty map.");

        }
    }

    private void writeMapToFile(String filename) throws IOException {
        JSONObject jsonObject = new JSONObject();
        synchronized (this.ipMapping) {
            for (int key : ipMapping.keySet()) {
                //System.out.println(key + "->" + this.ipMapping.get(key));
                jsonObject.put(key, ipMapping.get(key));
            }
        }
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(filename)));
        jsonObject.writeJSONString(out);
        out.flush();
        out.close();

    }
    private void readMapFromFile(String filename) throws FileNotFoundException, ParseException {

        BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(reader.lines().collect(Collectors.joining(System.lineSeparator())));
        synchronized (this.ipMapping) {
            this.ipMapping.clear();
            for (Object obj : jsonObject.keySet()) {
                long key = Long.parseLong((String) obj);
                this.ipMapping.put((int) key, (String) jsonObject.get(obj));
            }
        }
    }

    @PostMapping("/ns/addNode")
    public void addNode(@RequestParam int Id, @RequestParam String ip){
        synchronized (this.ipMapping) {
            this.ipMapping.put(Id, ip);
            try {
                writeMapToFile(this.mappingFile);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    @DeleteMapping("/ns/deleteNode")
    public void removeNode(@RequestParam int Id,@RequestParam String ip){
        synchronized (this.ipMapping) {
            if (this.ipMapping.containsKey(Id)) {
                this.ipMapping.remove(Id);
                try {
                    writeMapToFile(this.mappingFile);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }
    }

    @PutMapping("/ns/updateNode")
    public boolean updateNode(@RequestParam int Id,@RequestParam String ip){
        synchronized (this.ipMapping) {
            if (!this.ipMapping.containsKey(Id)) return false;
            this.ipMapping.put(Id, ip); //
            try {
                writeMapToFile(this.mappingFile);
            } catch (IOException exception) {
                exception.printStackTrace();
                return false;
            }
            return true;
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Starting NameServer");
        NameServer nameServer = new NameServer();
        nameServer.addNode(5, "192.168.0.5");
        nameServer.addNode(6, "192.168.0.6");
        nameServer.addNode(7, "192.168.0.7");
        nameServer.addNode(8, "192.168.0.8");
    }
}

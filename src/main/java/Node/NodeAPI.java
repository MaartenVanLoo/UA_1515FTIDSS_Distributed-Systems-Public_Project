package Node;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jade.core.Agent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.swing.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class NodeAPI {

    private HttpServer server;
    private Node node;
    private static final int HTTP_PORT = 8081;

    public NodeAPI(Node node) {
        this.node = node;
        try {
            this.server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            this.server.createContext("/node", (exchange) -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                if (!this.node.isSetUp()) {
                    exchange.sendResponseHeaders(400, -1);
                }
                if ("GET".equals(exchange.getRequestMethod())) {
                    String response = this.getNodeInfo();
                    OutputStream outputStream = exchange.getResponseBody();
                    exchange.sendResponseHeaders(200, response.length());
                    outputStream.write(response.getBytes());
                    outputStream.flush();
                    outputStream.close();
                }else if ("DELETE".equals(exchange.getRequestMethod())) {
                    String content = this.readContent(exchange);
                    try {
                        JSONObject jsonObject = (JSONObject) this.node.getParser().parse(content);
                        if (jsonObject.get("method").equals("shutdown")) {
                            System.out.println("Shutting down node");
                            this.node.shutdown(true);
                            exchange.sendResponseHeaders(200, -1);
                            System.out.println("Node shut down, exiting...");
                            System.exit(0);
                        } else if (jsonObject.get("method").equals("terminate")) {
                            System.out.println("Terminating node");
                            exchange.sendResponseHeaders(200, -1);
                            System.exit(0);
                        } else {
                            exchange.sendResponseHeaders(400, -1);
                        }
                    }
                    catch(Exception e) {
                        exchange.sendResponseHeaders(400, -1);
                    }
                }else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    //cross origin preflight request
                    exchange.sendResponseHeaders(200, -1);
                }
                else {
                    exchange.sendResponseHeaders(501, -1);
                }
                exchange.close();
            });
            this.server.createContext("/files", (exchange) -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                if (!this.node.isSetUp()) {
                    exchange.sendResponseHeaders(402, -1);
                }
                if ("GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(501, -1);
                }
                else if ("POST".equals(exchange.getRequestMethod())) {
                    //update file locations
                    boolean response = this.updateFileLocations(this.readContent(exchange));
                    if (response) {
                        exchange.sendResponseHeaders(200, -1);
                    }else{
                        exchange.sendResponseHeaders(404, -1);
                    }
                }
                else if ("DELETE".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(501, -1);
                }
                else if ("PUT".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(501, -1);
                }
                else if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    //cross origin preflight request
                    exchange.sendResponseHeaders(200, -1);
                }
                else{
                    exchange.sendResponseHeaders(501, -1);
                }
                exchange.close();
            });
            this.server.createContext("/agent", (exchange) -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                if (!this.node.isSetUp()) {
                    exchange.sendResponseHeaders(402, -1);
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    //run agent
                    runAgent(exchange);
                    exchange.sendResponseHeaders(200, -1);
                }
                else{
                    exchange.sendResponseHeaders(501, -1);
                }
                exchange.close();
            });
        } catch (Exception e) {
            this.server = null;
            System.out.println("Error creating http server");
        }
        this.start();
    }
    public boolean updateFileLocations(String requestBody) {
        try{
            System.out.println("####################################");
            System.out.println("Updating replicated file locations");
            System.out.println("Body: " + requestBody);
            System.out.println("####################################");
            JSONObject jsonObject = (JSONObject) this.node.getParser().parse(requestBody.toString());
            long newNodeId = (long) jsonObject.get("id");
            String newNodeIp = (String) jsonObject.get("ip");
            this.node.getFileManager().updateFileLocations(newNodeId, newNodeIp);
            System.out.println("Update replicated file locations complete");
        }catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public void start() {
        if (this.server != null) {
            this.server.setExecutor(Executors.newCachedThreadPool());
            this.server.start();
        }
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(1);
        }
    }

    private void runAgent(HttpExchange exchange) {
        //TODO add agent;
        try {
            //read body with agent
            ObjectInputStream ois = new ObjectInputStream(exchange.getRequestBody());
            Agent agent = (Agent) ois.readObject();
            Thread agentThread = new Thread(agent);
            agentThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private String getNodeInfo() {
        JSONObject response = new JSONObject();
        JSONObject node = new JSONObject();
        node.put("name", this.node.getName());
        node.put("ip", this.node.getIP());
        node.put("id", this.node.getId());

        JSONObject next = new JSONObject();
        next.put("id", this.node.getNextNodeId());
        next.put("ip", this.node.getNextNodeIP());

        JSONObject prev = new JSONObject();
        prev.put("id", this.node.getPrevNodeId());
        prev.put("ip", this.node.getPrevNodeIP());

        JSONArray local = new JSONArray();
        for (File file : this.node.getFileManager().getLocalFiles()) {
            local.add(file.getName());
        }

        JSONArray replicated = new JSONArray();
        for (File file : this.node.getFileManager().getReplicatedFiles()){
            replicated.add(file.getName());
        }

        JSONArray allFiles = new JSONArray();
        for (String file : this.node.getSyncAgent().getFileList()){
            allFiles.add(file);
        }

        response.put("node", node);
        response.put("next", next);
        response.put("prev", prev);
        response.put("local", local);
        response.put("replica", replicated);
        response.put("allFiles", allFiles);

        return response.toJSONString();
    }
    private String readContent(HttpExchange exchange) {
        StringBuilder content = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return content.toString();
    }
}

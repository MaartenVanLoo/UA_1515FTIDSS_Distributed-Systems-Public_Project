package Node;

import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;

import javax.swing.*;
import java.io.OutputStream;
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
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET");
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
                } else {
                    exchange.sendResponseHeaders(501, -1);
                }
                exchange.close();
            });
            this.server.createContext("/files", (exchange) -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                if (!this.node.isSetUp()) {
                    exchange.sendResponseHeaders(402, -1);
                }
                if ("GET".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(501, -1);
                }
                else if ("POST".equals(exchange.getRequestMethod())) {
                    //update file locations
                    boolean response = this.updateFileLocations(exchange.getRequestBody().toString());
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
            this.server.stop(0);
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

        response.put("node", node);
        response.put("next", next);
        response.put("prev", prev);

        return response.toJSONString();
    }
}

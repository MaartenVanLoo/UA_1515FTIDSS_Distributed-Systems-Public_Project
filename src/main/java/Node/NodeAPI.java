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

    public NodeAPI(Node node) {
        this.node = node;
        try {
            this.server = HttpServer.create(new InetSocketAddress(8081), 0);
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
            });
            this.server.createContext("/file/exchange", (exchange) -> {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String filename = exchange.getRequestURI().getQuery();
                    System.out.println("File exchange request for " + filename);
                    // success = FileTransfer.handleFileExchange(filename, exchange);
                    if (true){
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                        exchange.sendResponseHeaders(201, -1); // Created
                    }
                    else{
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    }
                }

            });
        } catch (Exception e) {
            this.server = null;
            System.out.println("Error creating http server");
        }

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

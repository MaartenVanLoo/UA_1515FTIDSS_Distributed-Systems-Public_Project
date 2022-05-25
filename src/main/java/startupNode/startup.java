package startupNode;

import Node.Node;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;

import java.io.OutputStream;
import java.net.InetSocketAddress;

public class startup {
    private HttpServer server;
    private static final int HTTP_PORT = 9000;

    public startup() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
            this.server.createContext("/start", (exchange) -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                if ("POST".equals(exchange.getRequestMethod())) {
                    Runtime r = Runtime.getRuntime();
                    Process p;
                    // Use bash -c so we can handle things like multi commands separated by ; and
                    // things like quotes, $, |, and \. My tests show that command comes as
                    // one argument to bash, so we do not need to quote it to make it one thing.
                    // Also, exec may object if it does not have an executable file as the first thing,
                    // so having bash here makes it happy provided bash is installed and in path.
                    String[] commands = {"bash", "-c", "./launch.sh"};
                    try {
                        p = r.exec(commands);
                        exchange.sendResponseHeaders(200, 0);
                        p.waitFor();
                    }catch (Exception e){
                        e.printStackTrace();
                        exchange.sendResponseHeaders(404, 0);
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
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public static void main(String[] args) {
        new startup();
    }
}

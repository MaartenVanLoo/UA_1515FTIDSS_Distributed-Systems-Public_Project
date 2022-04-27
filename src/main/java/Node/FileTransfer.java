package Node;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransfer extends Thread {
    private ServerSocket serverSocket;

    /**
     * Starts a server at a specified port and then waits for a TCP connection. If a connection is made, it starts a new
     * Thread for handling the connection and ending it.
     * @param port The port of the server socket.
     * @throws IOException If the creation of the ServerSocket fails.
     */
    public void startListener(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true){
            try {
                new Node.FileTransfer.NodeHandler(serverSocket.accept()).start();
            }
            catch (IOException exception){
                exception.printStackTrace();
                break;
            }
        }
    }

    /**
     * Closes the ServerSocket.
     * @throws IOException
     */
    public void stopListener() throws IOException {
        serverSocket.close();
    }

    /**
     * Class for handling a TCP connection in a different thread. (similar to Client.java)
     */
    private static class NodeHandler extends Thread{
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;

        public NodeHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public synchronized void start() {
            super.start();
        }

        @Override
        /**
         * Initializes the PrintWriter and BufferedReader. Then it reads the lines it receives until a period appears.
         * Finally it prints out the lines it received.
         */
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    if (".".equals(inputLine)) {
                        out.println("bye");
                        break;
                    }
                    out.println(inputLine); //echo input back to client
                    System.out.printf("Recieved: %s\n",inputLine);
                }

                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * Starts a server that accepts multiple TCP connections, each in a different threads
     */
    @Override
    public void run() {
        System.out.println("Starting server");
        System.out.println("Server host ip:");
        try {
            this.startListener(8888);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



}

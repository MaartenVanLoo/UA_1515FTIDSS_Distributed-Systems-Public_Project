package Node;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransfer extends Thread {
    private ServerSocket serverSocket;
    private static final int LISTENING_PORT = 8001;

    public static boolean sendFile(String fileName, String host, int port){
        try {
            Socket socket = new Socket(host, port);
            OutputStream outputStream = socket.getOutputStream();
            FileInputStream fileInputStream = new FileInputStream(new File(fileName));
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            fileInputStream.close();
            socket.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean sendFile(String fileName, String host) {
        return sendFile(fileName, host, LISTENING_PORT);
    }
    public FileTransfer() {
        this.start(); //start the thread;
    }

    public void startListener(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true) {
            try {
                new Node.FileTransfer.NodeHandler(serverSocket.accept()).start();
            } catch (IOException exception) {
                exception.printStackTrace();
                break;
            }
        }
    }

    public void stopListener() throws IOException {
        serverSocket.close();
    }

    private static class NodeHandler extends Thread {
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
                    System.out.printf("Recieved: %s\n", inputLine);
                }
                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        System.out.println("Starting server");
        System.out.println("Server host ip:");
        try {
            this.startListener(LISTENING_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

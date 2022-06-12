package Node;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.Random;

public class FileTransfer extends Thread {
    private ServerSocket serverSocket;
    private static final int LISTENING_PORT = 8001;
    private final Node node;

    public static boolean sendFile(String fileName, String sourceFolder, String targetFolder, String host, int port) {
        PrintWriter out;
        BufferedReader in;
        String targetFilePath = Objects.equals(targetFolder, "") ?fileName : targetFolder + "/" + fileName;
        String sourceFilePath = Objects.equals(sourceFolder, "") ?fileName : sourceFolder + "/" + fileName;
        try {
            Socket socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            File file = new File(sourceFilePath);
            if (!file.exists()) {
                System.out.println("FileTransfer:\t" + file.getName() + " does not exist");
                socket.close();
                return false;
            }
            long fileSize = file.length();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));

            byte[] buffer = new byte[1024];
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("fileName", targetFilePath);
            jsonObject.put("fileSize", fileSize);
            jsonObject.put("action", "create");
            out.println(jsonObject.toJSONString());
            out.flush();

            System.out.println("FileTransfer:\tSending: " + jsonObject.get("fileName")+" size: "+ fileSize);
            //receive acknowledgement of receiving file data
            String response = in.readLine();
            if(!response.equals("ACK")){
                System.out.println("FileTransfer:\tError sending file data");
                socket.close();
                return false;
            }

            //Now we send the file
            long current = 0;
            long total = 0;
            long startTime = System.currentTimeMillis();
            while((current = bufferedInputStream.read(buffer)) > 0){
                socket.getOutputStream().write(buffer, 0, (int)current);
                total += current;
                System.out.print("FileTransfer:\tSending file... " + (total * 100) / fileSize + "% complete!\r");
            }
            System.out.println("FileTransfer:\tSending file... 100% complete!");
            socket.getOutputStream().flush();
            bufferedInputStream.close();

            out.flush();
            out.close();
            in.close();
            socket.close();
            //System.out.println("\nFile sent successfully in "+ (System.currentTimeMillis() - startTime) +" ms!");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean sendFile(String fileName, String sourceFolder, String targetFolder, String host) {
        return sendFile(fileName, sourceFolder, targetFolder, host, LISTENING_PORT);
    }
    public static boolean sendFile(String fileName, String host, int port){
        return sendFile(fileName, "", "", host, port);
    }
    public static boolean sendFile(String fileName, String host) {
        return sendFile(fileName, host, LISTENING_PORT);
    }

    /**
     * Sends a request to the node where a certain file is replicated on, to delete that file.
     * @param fileName      name of the file to be deleted
     * @param targetFolder  folder where the file to be deleted is stored
     * @param host          node which has the replicated file
     * @param port          port of the node which has the replicated file
     * @return              true if the file is successfully deleted
     */
    public static boolean deleteFile(String fileName,String targetFolder, String host, int port){
        PrintWriter out;
        BufferedReader in;
        String targetFilePath = Objects.equals(targetFolder, "") ?fileName : targetFolder + "/" + fileName;
        try{
            Socket socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("fileName", targetFilePath);
            jsonObject.put("fileSize", -1);
            jsonObject.put("action", "delete");
            out.println(jsonObject.toJSONString());
            out.flush();
        }
        catch (IOException e){
            e.printStackTrace();
        }
        return true;
    }
    public static boolean deleteFile(String fileName, String targetFolder, String host) {
        return deleteFile(fileName, targetFolder, host, LISTENING_PORT);
    }
    public static boolean deleteFile(String fileName, String host, int port) {
        return deleteFile(fileName, "", host, port);
    }
    public static boolean deleteFile(String fileName, String host){
        return deleteFile(fileName, host, LISTENING_PORT);
    }

    /*
    TODO: Try to find a file without requesting the nameserver, based on the lecture peer2peer networks but never finished this.
    public static String getFileLocation(String fileName, String targetFolder, String host, int port, long TTL){
        System.out.println("getFile location not implemented yet");
        String targetFilePath = Objects.equals(targetFolder, "") ?fileName : targetFolder + "/" + fileName;
        PrintWriter out;
        BufferedReader in;
        try{
            Socket socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("fileName", targetFilePath);
            jsonObject.put("fileSize", -1);
            jsonObject.put("action", "getLocation");
            jsonObject.put("TTL", TTL);
            out.println(jsonObject.toJSONString());
            out.flush();
            String response = in.readLine();
            return response;
        }catch (IOException e){
            e.printStackTrace();
        }
        return "";
    }
    public static String getFileLocation(String fileName,String targetFolder,String host, int port){
        return getFileLocation(fileName, targetFolder, host, port, 64);
    }
    public static String getFileLocation(String fileName,String targetFolder,String host){
        return getFileLocation(fileName, targetFolder, host, LISTENING_PORT);
    }
    public static String getFileLocation(String fileName, String host, int port){
        return getFileLocation(fileName, "", host, port);
    }
    public static String getFileLocation(String fileName, String host){
        return getFileLocation(fileName, host, LISTENING_PORT);
    }
    public static String getFileLocation(String fileName, String host, long TTL){
        return getFileLocation(fileName, "", host, LISTENING_PORT, TTL);
    }
    */

    public FileTransfer(Node node) {
        this.node = node;
        this.setDaemon(true);
        this.start(); //start the thread;
    }

    private void startListener(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (!this.isInterrupted()) {
            try {
                new NodeHandler(serverSocket.accept(),this.node).start();
            } catch (IOException exception) {
                exception.printStackTrace();
                break;
            }
        }
    }

    public void stopListener() throws IOException {
        serverSocket.close();
    }

    /**
     * When a connection is accepted to receive a file for replication, then this class will further handle the reception of the file.
     */
    private static class NodeHandler extends Thread {
        private final Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private Node node;

        public NodeHandler(Socket clientSocket, Node node) {
            this.clientSocket = clientSocket;
            this.node = node;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String inputLine;

                //receive file name
                inputLine = in.readLine();
                if(inputLine == null){
                    System.out.println("FileTransfer:\tReceived null");
                    in.close();
                    out.close();
                    clientSocket.close();
                    throw new IOException("FileTransfer:\tFile name not received!");
                }

                JSONParser parser = new JSONParser();
                JSONObject metaData = (JSONObject) parser.parse(inputLine);
                String fileName = (String) metaData.get("fileName");
                long fileSize = (long) metaData.get("fileSize");
                String action = (String) metaData.get("action");

                switch (action) {
                    case "delete":
                        System.out.println("FileTransfer:\tDeleting" + fileName + " ...");
                        File file = new File(fileName);
                        if (file.exists()) {
                            file.delete();
                        }
                        break;
                    case "create":
                        //send filename received
                        out.println("ACK");
                        out.flush();
                        System.out.println("FileTransfer:\tReceiving file: " + fileName);

                        // receive file
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        int totalBytesRead = 0;
                        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
                        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                        while ((bytesRead = clientSocket.getInputStream().read(buffer)) != -1 && totalBytesRead < fileSize) {
                            bufferedOutputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                        }
                        bufferedOutputStream.flush();
                        bufferedOutputStream.close();
                        break;
                    case "getFileLocation":
                        out.println("");
                        /*
                        TODO: look for file, idea based in Peeer 2 Peer lecture but never finished
                        //check if file exists
                        File file = new File(fileName);
                        String location;
                        if (!file.exists()) {
                            long ttl = (long) metaData.get("TTL");
                            ttl--;
                            if (ttl <= 0){
                                location = "";
                            }else{
                                //search file in my own next neighbour
                                System.out.println("File not found in my files, ask my neighbour");
                                location = FileTransfer.getFileLocation(fileName, this.node.getNextNodeIP(),ttl);
                            }
                        }else{
                            location = this.node.getIP();
                        }
                        out.println(location);

                        */
                        break;
                }
                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException | ParseException exception) {
                exception.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        System.out.println("FileTransfer:\tStarting server");
        System.out.println("FileTransfer:\tServer host ip:");
        try {
            this.startListener(LISTENING_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

package Node;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.json.simple.JSONArray;
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
            System.out.println("File exists:" + file.exists());
            long fileSize = file.length();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));

            byte[] buffer = new byte[1024];
            JSONObject jsonObject = new JSONObject();
            Random rn = new Random();
            jsonObject.put("fileName", targetFilePath + "_copy" + rn.nextInt(100));
            jsonObject.put("fileSize", fileSize);
            jsonObject.put("action", "create");
            out.println(jsonObject.toJSONString());
            out.flush();

            System.out.println("Sending: " + jsonObject.get("fileName")+" "+ fileSize);
            //receive acknowledgement of receiving file data
            String response = in.readLine();
            System.out.println("Received: " + response);
            if(!response.equals("ACK")){
                System.out.println("Error sending file data");
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
                System.out.print("Sending file... " + (total * 100) / fileSize + "% complete!\r");
            }
            socket.getOutputStream().flush();
            bufferedInputStream.close();

            out.flush();
            out.close();
            in.close();
            socket.close();
            System.out.println("\nFile sent successfully in "+ (System.currentTimeMillis() - startTime) +" ms!");
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
        /*PrintWriter out;
        BufferedReader in;
        try {
            Socket socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            File file = new File(fileName);
            System.out.println("File exists:" + file.exists());
            long fileSize = file.length();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));

            byte[] buffer = new byte[1024];
            JSONObject jsonObject = new JSONObject();
            Random rn = new Random();
            jsonObject.put("fileName", fileName + "_copy" + rn.nextInt(100));
            jsonObject.put("fileSize", fileSize);
            out.println(jsonObject.toJSONString());
            out.flush();

            System.out.println("Sending: " + jsonObject.get("fileName")+" "+ fileSize);
            //receive acknowledgement of receiving file data
            String response = in.readLine();
            System.out.println("Received: " + response);
            if(!response.equals("ACK")){
                System.out.println("Error sending file data");
                socket.close();
                return false;
            }

            //Now we send the file
            long current = 0;
            long startTime = System.currentTimeMillis();
            while((current = bufferedInputStream.read(buffer)) > 0){
                socket.getOutputStream().write(buffer, 0, (int)current);
                System.out.print("Sending file... " + (current * 100) / fileSize + "% complete!\r");
            }
            socket.getOutputStream().flush();
            bufferedInputStream.close();

            out.flush();
            out.close();
            in.close();
            socket.close();
            System.out.println("\nFile sent successfully!");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }*/
    }
    public static boolean sendFile(String fileName, String host) {
        return sendFile(fileName, host, LISTENING_PORT);
    }

    public static boolean deleteFile(String fileName,String targetFolder, String host, int port){
        PrintWriter out;
        BufferedReader in;
        String targetFilePath = Objects.equals(targetFolder, "") ?fileName : targetFolder + "/" + fileName;
        try{
            Socket socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            JSONObject jsonObject = new JSONObject();
            Random rn = new Random();
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

    public static String getFileLocation(String fileName, String targetFolder, String host, int port, long TTL){
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
    public FileTransfer(Node node) {
        this.node = node;
        this.start(); //start the thread;
    }

    public void startListener(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        while (true) {
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
                System.out.println("Receiving file...");
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String inputLine;

                //receive file name
                inputLine = in.readLine();
                if(inputLine == null){
                    System.out.println("Received null");
                    in.close();
                    out.close();
                    clientSocket.close();
                    throw new IOException("File name not received!");
                }
                System.out.println("Raw: " + inputLine);
                System.out.println("Parsing");
                JSONParser parser = new JSONParser();
                JSONObject metaData = (JSONObject) parser.parse(inputLine);
                String fileName = (String) metaData.get("fileName");
                long fileSize = (long) metaData.get("fileSize");
                String action = (String) metaData.get("action");
                System.out.println("Received file name: " + fileName);
                System.out.println("Received file size: " + fileSize);
                System.out.println("Received action: " + action);

                if (action.equals("delete")) {
                    System.out.println("Deleting file...");
                    File file = new File(fileName);
                    if (file.exists()) {
                        file.delete();
                    }
                    return;
                }
                else if (action.equals("create")) {
                    //send fileneame recieved
                    out.println("ACK");
                    out.flush();
                    System.out.println("Sent ACK");

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
                }
                else if (action.equals("getFileLocation")) {
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
        System.out.println("Starting server");
        System.out.println("Server host ip:");
        try {
            this.startListener(LISTENING_PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

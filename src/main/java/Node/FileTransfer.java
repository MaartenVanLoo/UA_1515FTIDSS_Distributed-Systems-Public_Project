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
import java.util.Random;

public class FileTransfer extends Thread {
    private ServerSocket serverSocket;
    private static final int LISTENING_PORT = 8001;

    public static boolean sendFile(String fileName, String host, int port){
        PrintWriter out;
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
            out.write(jsonObject.toJSONString());
            out.flush();

            System.out.println("Sending: " + jsonObject.get("fileName")+" "+ fileSize);
            //receive acknowledgement of receiving file data
            String response = in.readLine();
            System.out.println("Received: " + response);
            if(!response.equals("ACK")){
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
            System.out.println("File sent successfully!");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    //creates file with name filename and writes content of httpExchange to it
    public static boolean handleFileExchange(String filename,  HttpExchange httpExchange) {
       try{
           File file = new File(filename);
           InputStream inputStream = httpExchange.getRequestBody();
           System.out.println("Read body" + httpExchange.getRequestBody().toString());
           OutputStream outputStream = new FileOutputStream(file);
           byte[] buffer = new byte[1024*8];
           int bytesRead;
           while((bytesRead = inputStream.read(buffer)) != -1){
               outputStream.write(buffer, 0, bytesRead);
           }
           System.out.print("File created!");
           outputStream.flush();
           IOUtils.closeQuietly(inputStream);
           IOUtils.closeQuietly(outputStream);
           return true; //file created successfully
       }
       catch (Exception e){
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
                new NodeHandler(serverSocket.accept()).start();
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

        public NodeHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
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

                System.out.println("Received file name: " + fileName);
                System.out.println("Received file size: " + fileSize);

                //send fileneame recieved
                out.println("ACK");
                out.flush();
                System.out.println("Sent ACK");

                // recieve file
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

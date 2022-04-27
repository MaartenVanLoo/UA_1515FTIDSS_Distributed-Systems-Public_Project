package Node;

import com.sun.net.httpserver.HttpExchange;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.json.simple.JSONObject;

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
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
            InputStream inputStream = socket.getInputStream();
            File file = new File(fileName);
            long fileSize = file.length();
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
            byte[] buffer = new byte[1024];
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("fileName", fileName);
            jsonObject.put("fileSize", fileSize);
            objectOutputStream.writeObject(jsonObject);
            outputStream.flush();
            System.out.println("Sending: " + jsonObject.get(fileName)+" "+jsonObject.get("fileSize"));
            //receive acknowledgement of receiving file data
            while (bufferedInputStream.read(buffer) > 0) {
                outputStream.write(buffer);
            }
            //jsonObject.("logSize",....);
            /*int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }*/
            //Now we send the file
            long current = 0;
            long startTime = System.currentTimeMillis();
            while(current != fileSize){
                int size = 10000;
                if(fileSize - current >= size){
                    current += size;
                }else{
                    size = (int)(fileSize - current);
                    current = fileSize;
                }
                buffer = new byte[size];
                bufferedInputStream.read(buffer, 0, size);
                outputStream.write(buffer);
                System.out.print("Sending file... " + (current * 100) / fileSize + "% complete!\r");
            }
            outputStream.flush();
            outputStream.close();
            fileInputStream.close();
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
           System.out.println("Read body");
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

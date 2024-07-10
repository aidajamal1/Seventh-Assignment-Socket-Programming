package chatapp;

import java.io.*;
import java.net.Socket;
import java.util.logging.*;

public class Client {
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private BufferedReader reader;
    private String username;
    private static final Logger logger = Logger.getLogger(Client.class.getName());

    public Client(String serverAddress, int serverPort) {
        setupLogging();
        initializeConnection(serverAddress, serverPort);
    }

    private void setupLogging() {
        try {
            FileHandler fh = new FileHandler("client.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeConnection(String serverAddress, int serverPort) {
        try {
            socket = new Socket(serverAddress, serverPort);
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
            reader = new BufferedReader(new InputStreamReader(System.in));
            requestUsername();
            startCommunication();
        } catch (IOException e) {
            handleError(e);
        } finally {
            closeConnection();
        }
    }

    private void requestUsername() throws IOException {
        System.out.print("Enter your username: ");
        username = reader.readLine();
        outputStream.writeUTF(username);
        logger.info("Username " + username + " sent to server.");
    }

    private void startCommunication() throws IOException {
        new Thread(new ServerHandler()).start();
        String message;
        while ((message = reader.readLine()) != null) {
            outputStream.writeUTF(message);
            logger.info("Message sent: " + message);
        }
    }

    private void handleError(IOException e) {
        logger.severe(e.getMessage());
        e.printStackTrace();
    }

    private void closeConnection() {
        try {
            inputStream.close();
            outputStream.close();
            socket.close();
            logger.info("Connection closed.");
        } catch (IOException e) {
            handleError(e);
        }
    }

    private class ServerHandler implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = inputStream.readUTF()) != null) {
                    System.out.println(message);
                    logger.info("Message received: " + message);
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    public static void main(String[] args) {
        new Client("localhost", 8888);
    }
}

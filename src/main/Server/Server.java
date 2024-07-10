package chatapp;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.logging.*;

public class Server {
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private Map<String, List<String>> chatHistory;
    private Map<String, File> availableFiles;
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    public Server(int port) {
        setupLogging();
        setupServer(port);
    }

    private void setupLogging() {
        try {
            FileHandler fh = new FileHandler("server.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            clients = Collections.synchronizedList(new ArrayList<>());
            chatHistory = new HashMap<>();
            availableFiles = new HashMap<>();
            loadFiles("data"); // Relative path to the data directory
            System.out.println("Server started on port " + port);
            logger.info("Server started on port " + port);
            acceptClients();
        } catch (IOException e) {
            handleError(e);
        } finally {
            closeServer();
        }
    }

    private void loadFiles(String directoryPath) {
        File directory = new File(directoryPath);
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    availableFiles.put(file.getName(), file);
                }
            }
            logger.info("Files loaded from directory: " + directoryPath);
        } else {
            logger.warning("No files found in directory: " + directoryPath);
        }
    }

    private void acceptClients() throws IOException {
        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(clientSocket);
            clients.add(clientHandler);
            new Thread(clientHandler).start();
            logger.info("New client connected: " + clientSocket.getRemoteSocketAddress());
        }
    }

    private void handleError(IOException e) {
        logger.severe(e.getMessage());
        e.printStackTrace();
    }

    private void closeServer() {
        try {
            for (ClientHandler client : clients) {
                client.closeConnection();
            }
            serverSocket.close();
            logger.info("Server closed.");
        } catch (IOException e) {
            handleError(e);
        }
    }

    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private DataInputStream inputStream;
        private DataOutputStream outputStream;
        private String username;

        public ClientHandler(Socket socket) {
            try {
                this.clientSocket = socket;
                this.inputStream = new DataInputStream(clientSocket.getInputStream());
                this.outputStream = new DataOutputStream(clientSocket.getOutputStream());
                this.username = inputStream.readUTF();
                welcomeClient();
                handleClientMessages();
            } catch (IOException e) {
                handleError(e);
            }
        }

        private void welcomeClient() throws IOException {
            sendMessage("Welcome, " + username + "!");
            sendMessage("Available commands:");
            sendMessage("1. /files - View available files");
            sendMessage("2. /download <filename> - Download a file");
            sendMessage("3. /history <count> - View chat history (optional count)");
            broadcastMessage(username + " joined the chat.");
            logger.info("Client " + username + " joined the chat.");
        }

        @Override
        public void run() {
            handleClientMessages();
        }

        private void handleClientMessages() {
            try {
                String message;
                while ((message = inputStream.readUTF()) != null) {
                    if (message.startsWith("/")) {
                        handleCommand(message);
                    } else {
                        broadcastMessage(username + ": " + message);
                        addToChatHistory(username, message);
                        logger.info("Message from " + username + ": " + message);
                    }
                }
            } catch (IOException e) {
                handleError(e);
            } finally {
                removeClient();
            }
        }

        private void handleCommand(String command) throws IOException {
            String[] parts = command.split(" ");
            switch (parts[0]) {
                case "/files":
                    sendAvailableFiles();
                    break;
                case "/download":
                    if (parts.length == 2) {
                        sendFile(parts[1]);
                    } else {
                        sendMessage("Invalid command. Usage: /download <filename>");
                    }
                    break;
                case "/history":
                    int count = parts.length == 2 ? Integer.parseInt(parts[1]) : 10;
                    sendChatHistory(count);
                    break;
                default:
                    sendMessage("Unknown command: " + parts[0]);
            }
        }

        private void sendAvailableFiles() throws IOException {
            StringBuilder response = new StringBuilder("Available files:\n");
            for (String filename : availableFiles.keySet()) {
                response.append(filename).append("\n");
            }
            sendMessage(response.toString());
        }

        private void sendFile(String filename) throws IOException {
            File file = availableFiles.get(filename);
            if (file != null) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                    sendMessage("File downloaded successfully.");
                    logger.info("File " + filename + " downloaded by " + username);
                }
            } else {
                sendMessage("File not found: " + filename);
                logger.warning("File not found: " + filename);
            }
        }

        private void sendChatHistory(int count) throws IOException {
            List<String> history = chatHistory.get(username);
            if (history != null) {
                int startIndex = Math.max(0, history.size() - count);
                StringBuilder response = new StringBuilder("Chat history:\n");
                for (int i = startIndex; i < history.size(); i++) {
                    response.append(history.get(i)).append("\n");
                }
                sendMessage(response.toString());
            } else {
                sendMessage("No chat history found.");
            }
        }

        private void addToChatHistory(String username, String message) {
            chatHistory.computeIfAbsent(username, k -> new ArrayList<>()).add(message);
        }

        private void sendMessage(String message) throws IOException {
            outputStream.writeUTF(message);
        }

        private void broadcastMessage(String message) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    try {
                        client.sendMessage(message);
                    } catch (IOException e) {
                        handleError(e);
                    }
                }
            }
        }

        private void removeClient() {
            clients.remove(this);
            broadcastMessage(username + " left the chat.");
            closeConnection();
            logger.info("Client " + username + " left the chat.");
        }

        private void closeConnection() {
            try {
                inputStream.close();
                outputStream.close();
                clientSocket.close();
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    public static void main(String[] args) {
        new Server(8888);
    }
}

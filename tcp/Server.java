package tcp;


import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Base64;

public class Server {
    private static final String host = "127.0.0.6";
    private static final int port = 55555;
    private static final String defaultPath = "./tcp/";

    private static final ArrayList<Socket> clients = new ArrayList<>();
    private static final ArrayList<String> nicknames = new ArrayList<>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("Server started on " + host + ":" + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connected with " + clientSocket);

                Thread thread = new Thread(new ClientHandler(clientSocket));
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter writer;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                writer = new PrintWriter(clientSocket.getOutputStream(), true);

                writer.println("NICK");
                String nickname = reader.readLine();
                nicknames.add(nickname);
                clients.add(clientSocket);

                System.out.println("Nickname is " + nickname);
                broadcast(nickname + " joined!");

                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.startsWith("/pm")) {
                        handlePM(message);
                    } else if (message.startsWith("/sendtxt")) {
                        handleSendTxt(message);
                    } else if (message.startsWith("/sendfile")) {
                        handleReceiveFile(message);
                    } else if (message.equals("/exit")) {
                        handleDisconnect(nickname);
                        break;
                    } else {
                        broadcast(nickname + ": " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void broadcast(String message) {
            for (PrintWriter clientWriter : getWriters()) {
                clientWriter.println(message);
            }
        }

        private ArrayList<PrintWriter> getWriters() {
            ArrayList<PrintWriter> writers = new ArrayList<>();
            for (Socket client : clients) {
                try {
                    writers.add(new PrintWriter(client.getOutputStream(), true));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return writers;
        }

        private void handlePM(String message) {
            String[] parts = message.split(" ", 3);
            String recipientNickname = parts[1];
            String privateMessage = parts[2];
            int recipientIndex = nicknames.indexOf(recipientNickname);
            if (recipientIndex != -1) {
                PrintWriter recipientWriter = getWriters().get(recipientIndex);
                recipientWriter.println("PM from " + nicknames.get(clients.indexOf(clientSocket)) + ": " + privateMessage);
            } else {
                writer.println(recipientNickname + " is not online.");
            }
        }

        private void handleSendTxt(String message) {
            String[] parts = message.split(" ", 3);
            String recipientNickname = parts[1];
            String filename = parts[2];
            String fullpath = defaultPath + filename;
            if (new File(fullpath).isFile()) {
                try (FileInputStream fileInputStream = new FileInputStream(fullpath)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        clientSocket.getOutputStream().write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                writer.println("File not found. Please check the filename and try again.");
            }
        }
        

        private void handleReceiveFile(String message) {
            String[] parts = message.split(" ", 3);
            String recipientNickname = parts[1];
            String filename = parts[2];
            int recipientIndex = nicknames.indexOf(recipientNickname);
            
            if (recipientIndex != -1) {
                Socket recipientSocket = clients.get(recipientIndex);
                try {
                    File dir = new File("./tcp_inbox/" + recipientNickname);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    String filePath = dir + "/" + filename;
                    FileOutputStream fos = new FileOutputStream(filePath);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                    byte[] data;
                    while (true) {
                        String line = reader.readLine();
                        if (line.equals("EOF")) {
                            break;
                        }
                        data = Base64.getDecoder().decode(line);
                        fos.write(data);
                    }
                    fos.close();

                    PrintWriter recipientWriter = new PrintWriter(recipientSocket.getOutputStream(), true);
                    recipientWriter.println("File " + filename + " received in your inbox.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                writer.println(recipientNickname + " is not online.");
            }
        }

        private void handleDisconnect(String nickname) {
            int index = nicknames.indexOf(nickname);
            nicknames.remove(index);
            clients.remove(index);
            broadcast(nickname + " left!");
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

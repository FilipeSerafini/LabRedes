package udp;

import java.io.*;
import java.net.*;
import java.util.Base64;

public class Client {
    private static final String defaultPath = "./udp/";
    private static final String serverIP = "127.0.0.5";
    private static final int serverPort = 55555;
    private static DatagramSocket clientSocket;
    private static String nickname;

    public static void main(String[] args) {
        try {
            clientSocket = new DatagramSocket();
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Choose your nickname: ");
            nickname = userInput.readLine();
            byte[] nicknameData = nickname.getBytes();
            DatagramPacket nicknamePacket = new DatagramPacket(nicknameData, nicknameData.length, InetAddress.getByName(serverIP), serverPort);
            clientSocket.send(nicknamePacket);

            Thread receiveThread = new Thread(new ReceiveThread());
            Thread writeThread = new Thread(new WriteThread(userInput));
            receiveThread.start();
            writeThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ReceiveThread implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    byte[] receiveData = new byte[1024];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    clientSocket.receive(receivePacket);
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    System.out.println(message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class WriteThread implements Runnable {
        private final BufferedReader userInput;

        public WriteThread(BufferedReader userInput) {
            this.userInput = userInput;
        }

        @Override
        public void run() {
            try {
                System.out.println("Type '/pm [nickname] [message]' to send a private message.");
                System.out.println("Type '/sendtxt [nickname] [filename]' to send a text file content.");
                System.out.println("Type '/sendfile [nickname] [filename]' to send a text file.");
                System.out.println("Type '/exit' to leave the chat room.");
                System.out.println("Type anything else to broadcast your message.");

                while (true) {
                    String message = userInput.readLine();
                    if (message != null) {
                        if (message.startsWith("/sendfile")) {
                            String[] parts = message.split(" ", 3);
                            String recipientNickname = parts[1];
                            String filename = parts[2];
                            String fullpath = defaultPath + filename;
                            if (new File(fullpath).isFile()) {
                                clientSocket.send(new DatagramPacket(message.getBytes(), message.getBytes().length, InetAddress.getByName(serverIP), serverPort));
                                sendFileInChunks(fullpath, recipientNickname);
                            } else {
                                System.out.println("File not found. Please check the filename and try again.");
                            }
                        } else {
                            clientSocket.send(new DatagramPacket(message.getBytes(), message.getBytes().length, InetAddress.getByName(serverIP), serverPort));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendFileInChunks(String filepath, String recipientNickname) throws IOException {
            try (FileInputStream fis = new FileInputStream(filepath)) {
                byte[] buffer = new byte[508]; // Adjusted for UDP payload size limitation
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] encodedChunk = Base64.getEncoder().encode(buffer);
                    DatagramPacket packet = new DatagramPacket(encodedChunk, encodedChunk.length, InetAddress.getByName(serverIP), serverPort);
                    clientSocket.send(packet);
                }
                byte[] endOfFileMessage = ("/endfile " + recipientNickname).getBytes();
                clientSocket.send(new DatagramPacket(endOfFileMessage, endOfFileMessage.length, InetAddress.getByName(serverIP), serverPort));
            }
        }
    }
}

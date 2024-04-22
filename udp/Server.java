package udp;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int port = 55555;
    private static DatagramSocket serverSocket;
    private static List<InetSocketAddress> clients = new ArrayList<>();
    private static List<String> nicknames = new ArrayList<>();
    private static Map<String, List<String>> fileDataChunks = new HashMap<>();

    public static void main(String[] args) {
        try {
            serverSocket = new DatagramSocket(port);
            System.out.println("Server is running...");

            Thread handleThread = new Thread();
            handleThread.start();
            
            while (true) {
                byte[] receiveData = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                if (!clients.contains(new InetSocketAddress(clientAddress, clientPort))) {
                    nicknames.add(message);
                    clients.add(new InetSocketAddress(clientAddress, clientPort));
                    broadcast((message + " joined!").getBytes());
                    System.out.println("Connected with " + clientAddress + ":" + clientPort + " with nickname " + message);
                    continue;
                }

                if (message.startsWith("/filedata")) {
                    String[] parts = message.split(" ", 3);
                    String recipientNickname = parts[1];
                    String encodedChunk = parts[2];
                    fileDataChunks.computeIfAbsent(recipientNickname, k -> new ArrayList<>()).add(encodedChunk);
                } else if (message.startsWith("/pm")) {
                    String[] parts = message.split(" ", 3);
                    String recipientNickname = parts[1];
                    String privateMessage = parts[2];
                    int senderIndex = clients.indexOf(new InetSocketAddress(clientAddress, clientPort));
                    String senderNickname = nicknames.get(senderIndex);
                    handlePm(senderNickname, recipientNickname, privateMessage, clientAddress, clientPort);
                } else if (message.startsWith("/sendtxt")) {
                    String[] parts = message.split(" ", 3);
                    String recipientNickname = parts[1];
                    String fileContents = parts[2];
                    int senderIndex = clients.indexOf(new InetSocketAddress(clientAddress, clientPort));
                    String senderNickname = nicknames.get(senderIndex);
                    handleSendTxt(senderNickname, recipientNickname, fileContents, clientAddress, clientPort);
                } else if (message.startsWith("/sendfile")) {
                    String[] parts = message.split(" ", 3);
                    String recipientNickname = parts[1];
                    String filename = parts[2];
                    fileDataChunks.put(recipientNickname + "_filename", Collections.singletonList(filename));
                } else if (message.startsWith("/endfile")) {
                    String recipientNickname = message.split(" ", 2)[1];
                    String filename = fileDataChunks.getOrDefault(recipientNickname + "_filename", Collections.emptyList()).get(0);
                    if (filename != null) {
                        List<String> encodedChunks = fileDataChunks.getOrDefault(recipientNickname, Collections.emptyList());
                        byte[] fileData = Base64.getDecoder().decode(String.join("", encodedChunks));
                        String recipientDir = "./udp_inbox/" + recipientNickname + "/";
                        new File(recipientDir).mkdirs();
                        String filePath = recipientDir + filename;
                        try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                            fileOutputStream.write(fileData);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        serverSocket.send(new DatagramPacket(("File " + filename + " successfully received.").getBytes(), ("File " + filename + " successfully received.").getBytes().length, clientAddress, clientPort));
                        // Clean up
                        fileDataChunks.remove(recipientNickname);
                        fileDataChunks.remove(recipientNickname + "_filename");
                    }
                } else if (message.equals("/exit")) {
                    int index = clients.indexOf(new InetSocketAddress(clientAddress, clientPort));
                    String nickname = nicknames.get(index);
                    handleExit(nickname, clientAddress, clientPort);
                } else {
                    int index = clients.indexOf(new InetSocketAddress(clientAddress, clientPort));
                    String senderNickname = nicknames.get(index);
                    // broadcast((senderNickname + ": " + message).getBytes(), new InetSocketAddress(clientAddress, clientPort));
                    broadcast((senderNickname + ": " + message).getBytes());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void broadcast(byte[] message) {
        for (InetSocketAddress client : clients) {
            try {
                serverSocket.send(new DatagramPacket(message, message.length, client));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handlePm(String senderNickname, String recipientNickname, String privateMessage, InetAddress senderAddress, int senderPort) {
        int recipientIndex = nicknames.indexOf(recipientNickname);
        if (recipientIndex != -1) {
            InetSocketAddress recipientClient = clients.get(recipientIndex);
            byte[] messageBytes = ("PM from " + senderNickname + ": " + privateMessage).getBytes();
            DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, recipientClient);
            try {
                serverSocket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            byte[] responseBytes = (recipientNickname + " is not online.").getBytes();
            DatagramPacket packet = new DatagramPacket(responseBytes, responseBytes.length, senderAddress, senderPort);
            try {
                serverSocket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleSendTxt(String senderNickname, String recipientNickname, String fileContents, InetAddress senderAddress, int senderPort) {
        int recipientIndex = nicknames.indexOf(recipientNickname);
        if (recipientIndex != -1) {
            InetSocketAddress recipientClient = clients.get(recipientIndex);
            byte[] messageBytes = ("File from " + senderNickname + ": " + fileContents).getBytes();
            DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, recipientClient);
            try {
                serverSocket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            byte[] responseBytes = (recipientNickname + " is not online.").getBytes();
            DatagramPacket packet = new DatagramPacket(responseBytes, responseBytes.length, senderAddress, senderPort);
            try {
                serverSocket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleExit(String nickname, InetAddress clientAddress, int clientPort) {
        int index = clients.indexOf(new InetSocketAddress(clientAddress, clientPort));
        if (index != -1) {
            // broadcast((nickname + " left!").getBytes(), new InetSocketAddress(clientAddress, clientPort));
            broadcast((nickname + " left!").getBytes());
            clients.remove(index);
            nicknames.remove(index);
        }
    }

    // private static void broadcast(byte[] message, InetSocketAddress inetSocketAddress) {
    //     for (InetSocketAddress client : clients) {
    //         try {
    //             serverSocket.send(new DatagramPacket(message, message.length, client));
    //         } catch (IOException e) {
    //             e.printStackTrace();
    //         }
    //     }
    // }
}

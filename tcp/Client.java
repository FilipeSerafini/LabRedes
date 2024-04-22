package tcp;

import java.io.*;
import java.net.*;
import java.util.Base64;

public class Client {
    private static String nickname;
    private static final String defaultPath = "./tcp/";
    private static final int serverPort = 55555;
    private static final String serverAddress = "127.0.0.6";

    public static void main(String[] args) {
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Choose your nickname: ");
        try {
            nickname = userInput.readLine();
            Socket clientSocket = new Socket(serverAddress, serverPort);
            Thread receiveThread = new Thread(new ReceiveThread(clientSocket));
            Thread writeThread = new Thread(new WriteThread(clientSocket, userInput));
            receiveThread.start();
            writeThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ReceiveThread implements Runnable {
        private final Socket socket;

        public ReceiveThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (true) {
                    String message = reader.readLine();
                    if (message != null) {
                        System.out.println(message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class WriteThread implements Runnable {
        private final Socket socket;
        private final BufferedReader userInput;

        public WriteThread(Socket socket, BufferedReader userInput) {
            this.socket = socket;
            this.userInput = userInput;
        }

        @Override
        public void run() {
            try {
                System.out.println("Commands:\n'/pm [nickname] [message]'\n'/sendtxt [nickname] [filename]'\n'/sendfile [nickname] [filename]'\n'/exit'");
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                while (true) {
                    String message = userInput.readLine();
                    if (message != null) {
                        if (message.startsWith("/sendfile")) {
                            String[] parts = message.split(" ", 3);
                            String recipientNickname = parts[1];
                            String filename = parts[2];
                            String fullpath = defaultPath + filename;
                            try {
                                File file = new File(fullpath);
                                FileInputStream fis = new FileInputStream(file);
                                byte[] fileData = new byte[(int) file.length()];
                                fis.read(fileData);
                                fis.close();
                                String encodedData = Base64.getEncoder().encodeToString(fileData);
                                String header = "/sendfile " + recipientNickname + " " + filename;
                                writer.println(header);
                                writer.println(" "); //Delimitar ou separar mensagem
                                writer.println(encodedData);
                                writer.println("EOF");
                            } catch (FileNotFoundException e) {
                                System.out.println("File not found. Please check the filename and try again.");
                            }
                        } else if (message.startsWith("/sendtxt")) {
                            String[] parts = message.split(" ", 3);
                            String recipientNickname = parts[1];
                            String filename = parts[2];
                            String fullpath = defaultPath + filename;
                            try {
                                BufferedReader fileReader = new BufferedReader(new FileReader(fullpath));
                                StringBuilder contents = new StringBuilder();
                                String line;
                                while ((line = fileReader.readLine()) != null) {
                                    contents.append(line).append("\n");
                                }
                                fileReader.close();
                                String fileMessage = "/sendtxt " + recipientNickname + " " + contents;
                                writer.println(fileMessage);
                            } catch (FileNotFoundException e) {
                                System.out.println("File not found. Please check the filename and try again.");
                            }
                        } else {
                            writer.println(message);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

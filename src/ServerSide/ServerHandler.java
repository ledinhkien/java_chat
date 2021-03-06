package ServerSide;

import Utilities.ServerFile;

import javax.swing.text.BadLocationException;
import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class ServerHandler extends Thread {

    private Socket socket;
    private DataInputStream inputFromClient;
    private Server server;

    public ServerHandler(Socket socket, Server server) {
        this.server = server;
        this.socket = socket;
    }

    public void run() {
        try {
            inputFromClient = new DataInputStream(socket.getInputStream());

            while (true) {
                // InputStream has no more data => go back to the beginning, if yes => keep going
                // TODO: this causes buffer overflow
//                if (inputFromClient.available() == 0) {
//                    continue;
//                }

                // Get message from InputStream
                // message = type!:sender!:content!:receiver
                String message = "";

                try {
                    message = inputFromClient.readUTF();
                } catch (EOFException ex) {
                    try {
                        inputFromClient.close();
                        this.stop();
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                String splitMsg[] = message.split("!:");

                switch (splitMsg[0]) {
                    case "message":
                        // Normal message
                        if (splitMsg.length == 4) {
                            // Private Message
                            String[] receiverList = splitMsg[3].replace("[", "").replace("]", "").split(", ");
                            if (receiverList.length > 1) {
                                ArrayList<Integer> positionList = new ArrayList<Integer>();
                                ArrayList<Socket> tempSocketList = new ArrayList<Socket>();
                                for (int i = 0; i < receiverList.length; i++) {
                                    int position = server.userList.indexOf(receiverList[i]);
                                    positionList.add(position);
                                }
                                for (int i = 0; i < positionList.size(); i++) {
                                    Socket tempSocket = server.socketArray.get(positionList.get(i));
                                    tempSocketList.add(tempSocket);
                                }
                                for (int i = 0; i < tempSocketList.size(); i++) {
                                    DataOutputStream outputToClients = new DataOutputStream(tempSocketList.get(i).getOutputStream());
                                    outputToClients.writeUTF("<Private message to " + splitMsg[3] + "> " + splitMsg[1] + " : " + splitMsg[2] + "\n");
                                    outputToClients.flush();
                                }
                            } else {
                                sendOneUser(receiverList[0], "<Private message to you> " + splitMsg[1] + " : " + splitMsg[2] + "\n");
                            }

                        } else {
                            // Send message received to all clients
                            sendToAll(splitMsg[1] + " : " + splitMsg[2] + "\n");
//                            serverDisplayChat(splitMsg[1] + " : " + splitMsg[2] + "\n");
                        }

                        break;
                    case "login":
                        // Add new user to userList
                        server.userList.add(splitMsg[1]);
                        sendToAll(splitMsg[1] + " connected to chat" + "\n");
//                        ServerGUI.systemLog.append(splitMsg[1] + " connected" + "\n");

                        break;
                    case "logout":
                        // Remove user from userList
                        int position = 0;
                        for (int i = 0; i < server.userList.size(); i++) {
                            if (server.userList.get(i).contains(splitMsg[1])) {
                                position = i;
                            }
                        }
//                        ServerGUI.systemLog.append(splitMsg[1] + " disconnected" + "\n");
                        server.socketArray.remove(position);
                        sendToAll(splitMsg[1] + " disconnected from chat" + "\n");
                        server.userList.remove(position);
                        System.out.println("Client disconnected from: " + socket.getLocalAddress().getHostName() + "/" + socket.getPort());

                        break;
                    case "requestSave":
                        // Start server to receive file
                        ServerFile serverFile = new ServerFile(splitMsg[1], splitMsg[2], splitMsg[2]);
                        if (!serverFile.isAlive()) {
                            serverFile.start();
                        }

                        // message = type!:sender!:port!:hostAddress!:filePath
                        String msg = "acceptFile" + "!:" + "server" + "!:" + serverFile.getPort() + "!:"
                                + serverFile.getAddress() + "!:" + splitMsg[4];

                        // tell client to start sending file
                        sendOneUser(splitMsg[1], msg);

                        if (!splitMsg[3].equals("server")) {
                            // Send request to specific user
                            // message = type!:sender!:fileName!:port!:hostAddress
                            String mess = splitMsg[0] + "!:" + splitMsg[1] + "!:" + splitMsg[2] + "!:" + serverFile.getPort() + "!:" + serverFile.getAddress() + "!:" + splitMsg[4];
                            sendOneUser(splitMsg[3], mess);
                        }

                        break;
                    case "acceptFile":
                        // Send accept message back to sender
                        sendOneUser(splitMsg[3], message);

                        // Send emoticon
                        break;
                    case "emoticon":

                        if (splitMsg.length == 4) {
                            // Private Message
                            sendOneUser(splitMsg[3], message);
                        } else {
                            // Send message received to all clients
                            sendToAll(message);
//                            ServerGUI.insertEmoticon(splitMsg[1], splitMsg[2]);
                        }

                        break;
                    case "changeStatus":
                        for (int i = 0; i < server.userList.size(); i++) {
                            if (server.userList.get(i).contains(splitMsg[1])) {
                                server.userList.set(i, splitMsg[1] + " - " + splitMsg[2]);
                            }
                        }
                        break;
                }

                // Refresh user in chat list
                refreshJList();
//                this.serverGUI.usersJlist.setListData(server.userList.toArray());
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Refresh the "People in chat" in client's GUI
    private void refreshJList() throws IOException {
        for (int i = 0; i < server.socketArray.size(); i++) {
            Socket tempSocket = server.socketArray.get(i);
            DataOutputStream outputFromServer = new DataOutputStream(tempSocket.getOutputStream());
            outputFromServer.writeUTF("refresh" + "!:" + server.userList);
            outputFromServer.flush();
        }
    }

    private void sendToAll(String str) throws IOException {
        for (int i = 0; i < server.socketArray.size(); i++) {
            Socket tempSocket = server.socketArray.get(i);
            DataOutputStream outputFromServer = new DataOutputStream(tempSocket.getOutputStream());
            outputFromServer.writeUTF(str);
            outputFromServer.flush();
        }
    }

    private void sendOneUser(String user, String msg) throws IOException {
        // Find specific user
        int position = server.userList.indexOf(user);
        Socket tempSocket = server.socketArray.get(position);

        // Send message
        DataOutputStream outputToClient = new DataOutputStream(tempSocket.getOutputStream());
        outputToClient.writeUTF(msg);
        outputToClient.flush();
    }

//    public void serverDisplayChat(String str) {
//        try {
//            ServerGUI.showChat.insertString(ServerGUI.showChat.getLength(), str, null);
//        } catch (BadLocationException ex) {
//            ex.printStackTrace();
//        }
//    }
}

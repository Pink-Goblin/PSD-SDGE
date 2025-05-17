package com.sc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONObject;

public class Server {
    private ServerSocketChannel ServerSocket;

    private List<Client> Clients;
    private List<Group> Groups;

    public Server(int port) {
        Clients = new LinkedList<Client>();
        Groups = new LinkedList<Group>();
        Groups.add(new Group("Debug"));

        try {
            System.out.println("[Chat Server] Listening on port " + port + "...");
            ServerSocket = ServerSocketChannel.open();
            ServerSocket.bind(new InetSocketAddress(port));
            ServerSocket.configureBlocking(false);

            while (true) {
                SocketChannel channel = ServerSocket.accept();

                if (channel != null) {
                    addClient(channel);
                }

                for (Client client : Clients) {
                    try {
                        client.process();

                        if (client.ReadQueue.isEmpty())
                            continue;

                        JSONObject readJSON = client.ReadQueue.remove();
                        String method = readJSON.getString("method");

                        if (method.equals("message")) {
                            Message readMessage = new Message(readJSON);
                            Group targetGroup = getGroup(readMessage.Group);
                            targetGroup.addMessage(readMessage, client);
                        } else if (method.equals("connect_to_group")) {
                            Group targetGroup = getGroup(readJSON.getString("group"));
                            targetGroup.addMember(client);
                        }

                    } catch (IOException e) {
                        System.out.println("[IOException] " + e.toString());
                        removeClient(client);
                    }
                }

                for (Group group : Groups) {
                    group.receiveMessages();
                }
            }
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
        }
    }

    public void addClient(SocketChannel channel) {
        try {
            Client newClient = new Client(channel);
            Clients.add(newClient);
            System.out.println("[Chat Server] Client " + newClient.getId() + " connected");
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
        }
    }

    public void removeClient(Client client) {
        System.out.println("[Chat Server] Client " + client.getId() + " disconnected");
        Clients.remove(client);
        for (Group group : Groups) {
            group.removeMember(client);
        }
    }

    public Group getGroup(String name) {
        for (Group group : Groups) {
            if (group.GroupName.equals(name))
                return group;
        }
        return null;
    }
}

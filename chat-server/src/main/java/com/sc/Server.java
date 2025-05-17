package com.sc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;

public class Server {
    private ServerSocketChannel ServerSocket;

    private List<Client> Clients;
    private List<Group> Groups;

    public Server(int port) {
        Clients = new LinkedList<Client>();
        Groups = new LinkedList<Group>();

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
            System.out.println("[Chat Server] Client connected");
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
        }
    }
}

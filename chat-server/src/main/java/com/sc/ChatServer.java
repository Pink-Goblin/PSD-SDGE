package com.sc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

import org.json.JSONObject;

public class ChatServer {
    private int READ_MAX_CHARS = 300;

    private SocketAddress Address;
    private SocketChannel Connection;

    public ChatServer(String host, int port) throws ClosedChannelException, IOException {
        this.Address = new InetSocketAddress(host, port);
        this.Connection = SocketChannel.open();
        Connection.connect(Address);
        Connection.configureBlocking(false);
    }

    public Message getMessage() {
        try {
            ByteBuffer readBuffer = ByteBuffer.allocate(READ_MAX_CHARS);
            Connection.read(readBuffer);
            String message = readBuffer.toString();
            JSONObject json = new JSONObject(message);
            return new Message(json);
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
            return null;
        }
    }

    public void postMessage(Message message) {
        try {
            byte[] bytes = message.toString().getBytes();
            ByteBuffer writeBuffer = ByteBuffer.wrap(bytes);
            Connection.write(writeBuffer);
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
        }
    }

    public void disconnect() {
        try {
            Connection.close();
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
        }
    }
}

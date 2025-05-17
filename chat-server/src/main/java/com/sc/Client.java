package com.sc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

public class Client {
    private int READ_MAX_CHARS = 300;

    private SocketChannel Connection;

    public Client(SocketChannel channel) throws ClosedChannelException, IOException {
        this.Connection = channel;
        Connection.configureBlocking(false);
    }

    public Message getMessage() {
        try {
            ByteBuffer readBuffer = ByteBuffer.allocate(READ_MAX_CHARS);
            Connection.read(readBuffer);
            String message = readBuffer.toString();
            return new Message(message);
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
            return null;
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

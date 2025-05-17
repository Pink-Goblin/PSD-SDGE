package com.sc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import org.json.JSONObject;

public class Client {
    private int READ_MAX_CHARS = 1024;

    private String Id;
    private SocketChannel Connection;

    public Queue<JSONObject> ReadQueue;
    private Queue<String> WriteQueue;

    public Client(SocketChannel channel) throws ClosedChannelException, IOException {
        this.Id = UUID.randomUUID().toString();

        this.Connection = channel;
        Connection.configureBlocking(false);

        this.ReadQueue = new LinkedList<JSONObject>();
        this.WriteQueue = new LinkedList<String>();
    }

    public String getId() {
        return Id;
    }

    public boolean isConnected() {
        return Connection.isConnected();
    }

    public void process() throws IOException {
        connect();
        read();
        write();
    }

    private void connect() throws IOException {
        if (Connection.isConnectionPending()) {
            Connection.finishConnect();
            System.out.println("[Chat Server] Client " + Id + " connected");
        }
    }

    private void read() throws IOException {

        ByteBuffer readBuffer = ByteBuffer.allocate(READ_MAX_CHARS);
        int bytesRead = Connection.read(readBuffer);

        if (bytesRead > 0) {
            readBuffer.flip();
            String message = new String(readBuffer.array(), 0, readBuffer.limit());
            System.out.println("[Client] Received message from " + Id + ": " + message);

            JSONObject json = new JSONObject(message);
            ReadQueue.add(json);
        } else if (bytesRead == -1) {
            Connection.close();
            System.out.println("[Client] " + Id + " closed the connection.");
        }
    }

    private void write() throws IOException {
        if (WriteQueue.isEmpty()) {
            return;
        }

        String message = WriteQueue.remove();
        byte[] bytesWrite = message.toString().getBytes();
        ByteBuffer writeBuffer = ByteBuffer.wrap(bytesWrite);
        Connection.write(writeBuffer);
        System.out.println("[Server] Sent Client " + Id + " a message: " + message);
    }

    public void sendMessage(String message) {
        WriteQueue.add(message);
    }

    public void disconnect() {
        try {
            Connection.close();
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
        }
    }
}

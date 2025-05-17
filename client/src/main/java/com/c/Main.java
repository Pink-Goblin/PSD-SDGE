package com.c;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;

public class Main {
    public static void main(String[] args) {
        System.out.println("[Client] Starting...");

        System.out.println("[Client] Connecting to chat server localhost:3000");
        try {
            ChatServer cs = new ChatServer("localhost", 3000);
        } catch (ClosedChannelException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
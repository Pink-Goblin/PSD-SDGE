package com.sc;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            Server server = new Server(3000);
        } catch (Exception e) {
            System.out.println("! SERVER CRASH !");
            e.printStackTrace();

            try {
                System.in.read();
            } catch (IOException ioe) {
            }
        }
    }
}
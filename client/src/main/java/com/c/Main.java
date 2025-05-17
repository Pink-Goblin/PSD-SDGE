package com.c;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {

        try {
            UI.displayMenu(true);
        } catch (Exception e) {
            System.out.println("! CLIENT CRASH !");
            e.printStackTrace();

            try {
                System.in.read();
            } catch (IOException ioe) {
            }
        }
    }
}
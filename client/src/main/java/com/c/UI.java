package com.c;

import java.io.IOException;
import java.util.Map;

public class UI {
    final static String operatingSystem = System.getProperty("os.name");

    public static void clear() {
        try {
            if (operatingSystem.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                Runtime.getRuntime().exec(new String[] { "clear" });
            }
        } catch (IOException e) {
            System.out.println("[IOException] " + e.toString());
        } catch (InterruptedException e) {
            System.out.println("[InterruptedException] " + e.toString());
        }
    }

    public static void displayMenu(String lastError) {
        clear();
        System.out.println("- - - ERROR - - -");
        System.out.println(lastError);
        displayMenu(false);
    }

    public static void displayMenu(boolean clear) {
        if (clear)
            clear();
        System.out.println("- - - CLIENT - - -");
        System.out.println("Welcome " + Client.getUsername() + "!");

        System.out.println("- - - MENU - - -");
        String[] menuOptions = new String[] {
                "Change username",
                "Join group chat",
                "Create group chat"
        };

        for (int i = 0; i < menuOptions.length; i++) {
            System.out.println((i + 1) + ". " + menuOptions[i]);
        }
        System.out.println("- - - - - - - - -");

        int input = Utils.ReadInt();
        System.out.println("Input: " + input);
        switch (input) {
            case 1:
                displayChangeUsername();
                break;
            case 2:
                displayGroupChats();
                break;
            case 3:
                displayCreateGroupChat();
                break;
            default:
                displayMenu(true);
        }

    }

    public static void displayChangeUsername() {
        clear();
        System.out.println("- - - Change username - - -");
        System.out.println("Old username: " + Client.getUsername());
        System.out.print("New username: ");

        String new_username = Utils.ReadToken();
        Client.setUsername(new_username);
        displayMenu(true);
    }

    public static void displayGroupChats() {
        clear();
        System.out.println("- - - Group chats - - -");

        Map<String, String> groups = Utils.getGroups();
        Object[] names = groups.keySet().toArray();

        for (int i = 0; i < names.length; i++) {
            System.out.println((i + 1) + ". " + names[i]);
        }
        System.out.println("- - - - - - - - -");

        int input = Utils.ReadInt();
        int index = input - 1;
        if (index < 0 || index >= names.length) {
            displayGroupChats();
        }
        String group = (String) names[index];
        displayChat(group, groups.get(group));
    }

    public static void displayCreateGroupChat() {
        clear();
        System.out.println("- - - Create group chat - - -");
        System.out.println("Not implemented yet!");

        Utils.ReadAny();
        displayMenu(true);
    }

    public static void displayChat(String groupName, String serverAddress) {
        clear();

        ChatLog chatLog = new ChatLog();

        String[] splitAddress = serverAddress.split(":");
        String host = splitAddress[0];
        int port = Integer.parseInt(splitAddress[1]);

        chatLog.addLog("[" + Client.getUsername() + "] Connecting to chat server " + serverAddress);
        try {
            ChatServer cs = new ChatServer(host, port);
            chatLog.addLog("[" + Client.getUsername() + "] Connected to chat group: " + groupName);

            boolean exit = false;

            cs.connectToGroup(groupName);

            AsyncReadAny readAny = new AsyncReadAny();
            Thread thread = new Thread(readAny);
            thread.start();

            clear();
            chatLog.printLog();

            while (!exit) {
                Message new_message = cs.getMessage();
                if (new_message != null) {
                    chatLog.addLog(new_message.toChatLog());
                    clear();
                    chatLog.printLog();
                }

                String input = "";

                if (readAny.hasInput()) {
                    input = readAny.getInput().trim();
                    readAny = new AsyncReadAny();
                    thread = new Thread(readAny);
                    thread.start();
                }

                if (input.equals("/exit")) {
                    exit = true;
                    continue;
                } else if (input.equals("")) {
                    continue;
                }

                Message message = new Message(groupName, input, Client.getUsername());
                cs.postMessage(message);
            }

            thread.interrupt();
            cs.disconnect();
            displayMenu(true);
        } catch (Exception e) {
            displayMenu(true);
        }
    }
}

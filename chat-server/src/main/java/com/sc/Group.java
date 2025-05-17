package com.sc;

import java.util.ArrayList;
import java.util.List;

public class Group {
    public String GroupName;
    private List<Client> Members;
    private List<Message> ChatLog;
    private List<ChatServer> Servers;

    public Group(String name) {
        this.GroupName = name;
        this.Members = new ArrayList<Client>();
        this.ChatLog = new ArrayList<Message>();
        this.Servers = new ArrayList<ChatServer>();
    }

    public void addMember(Client client) {
        Members.add(client);
    }

    public void removeMember(Client client) {
        Members.remove(client);
    }

    public void receiveMessages() {
        for (ChatServer server : Servers) {
            Message newMessage = server.getMessage();
            ChatLog.add(newMessage);
        }
    }

    public void addMessage(Message message, Client sender) {
        ChatLog.add(message);

        for (ChatServer server : Servers) {
            server.postMessage(message);
        }

        for (Client client : Members) {
            client.sendMessage(message.toString());
        }
    }
}

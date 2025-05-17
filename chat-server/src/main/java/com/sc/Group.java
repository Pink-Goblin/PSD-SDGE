package com.sc;

import java.util.ArrayList;
import java.util.List;

public class Group {
    private String GroupName;
    private List<String> Members;
    private List<Message> ChatLog;
    private List<ChatServer> Servers;

    public Group(String name, String firstMember) {
        this.GroupName = name;
        this.Members = new ArrayList<String>();
        Members.add(firstMember);
        this.ChatLog = new ArrayList<Message>();
        this.Servers = new ArrayList<ChatServer>();
    }

    public void receiveMessages() {
        for (ChatServer server : Servers) {
            Message newMessage = server.getMessage();
            ChatLog.add(newMessage);
        }
    }

    public void addMessage(String text, String sender) {
        if (text == "")
            return;
        if (sender == "")
            return;

        Message m = new Message(text, sender);
        ChatLog.add(m);

        for (ChatServer server : Servers) {
            server.postMessage(m);
        }
    }
}

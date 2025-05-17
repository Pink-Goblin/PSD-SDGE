package com.c;

import java.util.LinkedList;
import java.util.List;

public class ChatLog {
    private List<String> chatLog;

    public ChatLog() {
        chatLog = new LinkedList<>();
    }

    public void addLog(String log) {
        chatLog.add(log);
    }

    public void printLog() {
        for (String log : chatLog) {
            System.out.println(log);
        }
    }
}

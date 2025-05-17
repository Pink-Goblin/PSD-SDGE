package com.c;

import java.time.LocalTime;

public class Message {
    public String Text;
    public String Sender;
    public int Timestamp;

    public Message(String text, String sender) {
        this.Text = text;
        this.Sender = sender;
        this.Timestamp = LocalTime.now().toSecondOfDay();
    }

    public Message(String message) {
        String[] pipe_split = message.split(" | ");

        this.Timestamp = Integer.parseInt(pipe_split[0]);
        this.Sender = pipe_split[1];
        this.Text = pipe_split[2];
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        return sb.append(Timestamp)
                .append(" | ")
                .append(Sender)
                .append(" | ")
                .append(Text)
                .toString();
    }
}

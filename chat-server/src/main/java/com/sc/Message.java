package com.sc;

import org.json.JSONObject;

public class Message {
    public String Group;
    public String Text;
    public String Sender;
    public long Timestamp;

    public Message(String group, String text, String sender) {
        this.Group = group;
        this.Text = text;
        this.Sender = sender;
        this.Timestamp = System.currentTimeMillis();
    }

    public Message(JSONObject json) {
        this.Group = json.getString("group");
        this.Text = json.getString("text");
        this.Sender = json.getString("sender");
        this.Timestamp = json.getInt("timestamp");
    }

    public String toString() {
        JSONObject json = new JSONObject();
        json.put("method", "message");
        json.put("timestamp", Timestamp);
        json.put("group", Group);
        json.put("sender", Sender);
        json.put("text", Text);
        return json.toString();
    }
}

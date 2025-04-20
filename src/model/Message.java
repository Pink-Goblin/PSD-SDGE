package com.chatapp.model;

import java.util.UUID;

public class Message {
    private final String id;
    private final String userId;
    private final String topic;
    private final String content;
    private final VectorClock vectorClock;
    private final long timestamp;

    public Message(String userId, String topic, String content, VectorClock vectorClock) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.topic = topic;
        this.content = content;
        this.vectorClock = vectorClock;
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String id, String userId, String topic, String content, VectorClock vectorClock, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.topic = topic;
        this.content = content;
        this.vectorClock = vectorClock;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTopic() {
        return topic;
    }

    public String getContent() {
        return content;
    }

    public VectorClock getVectorClock() {
        return vectorClock;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Message{userId='" + userId + "', topic='" + topic + "', content='" + content + "'}";
    }
}
package com.chatapp.server;

import com.chatapp.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogManager {
    private static final Logger logger = LoggerFactory.getLogger(LogManager.class);

    private final String baseLogDir;
    private final Map<String, File> topicLogFiles = new ConcurrentHashMap<>();

    public LogManager(String baseLogDir) {
        this.baseLogDir = baseLogDir;

        // Create log directory if it doesn't exist
        File logDir = new File(baseLogDir);
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                logger.error("Failed to create log directory: {}", baseLogDir);
            }
        }
    }

    /**
     * Appends a message to the log for a specific topic
     */
    public synchronized void appendMessage(Message message) {
        String topic = message.getTopic();
        File logFile = getLogFile(topic);

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println(serializeMessage(message));
        } catch (IOException e) {
            logger.error("Failed to write message to log for topic {}: {}", topic, e.getMessage());
        }
    }

    /**
     * Reads all messages from the log for a specific topic
     */
    public List<Message> readMessages(String topic) {
        List<Message> messages = new ArrayList<>();
        File logFile = getLogFile(topic);

        if (!logFile.exists()) {
            return messages;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Message message = deserializeMessage(line);
                if (message != null) {
                    messages.add(message);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read messages from log for topic {}: {}", topic, e.getMessage());
        }

        return messages;
    }

    /**
     * Gets the log file for a specific topic
     */
    private File getLogFile(String topic) {
        return topicLogFiles.computeIfAbsent(topic, t -> {
            String sanitizedTopic = t.replaceAll("[^a-zA-Z0-9-_]", "_");
            return new File(baseLogDir, sanitizedTopic + ".log");
        });
    }

    /**
     * Serializes a message to a string
     */
    private String serializeMessage(Message message) {
        // Simple CSV format: id,userId,topic,content,timestamp,vectorClock
        return String.join(",",
                message.getId(),
                message.getUserId(),
                message.getTopic(),
                message.getContent().replace(",", "\\,"),
                String.valueOf(message.getTimestamp()),
                message.getVectorClock().toString().replace(",", "\\,")
        );
    }

    /**
     * Deserializes a message from a string
     */
    private Message deserializeMessage(String line) {
        // TODO: Implement proper deserialization
        // This is a placeholder for now
        return null;
    }
}
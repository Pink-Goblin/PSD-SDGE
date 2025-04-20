package com.chatapp.server;

import com.chatapp.model.Message;
import com.chatapp.model.ORSet;
import com.chatapp.model.VectorClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TopicHandler {
    private static final Logger logger = LoggerFactory.getLogger(TopicHandler.class);

    // Topic information
    private final String topicName;
    private final String serverId;

    // Other SCs serving this topic
    private final List<SCAddress> peerServers = new CopyOnWriteArrayList<>();

    // User list as CRDT (Observed-Remove Set)
    private final ORSet<String> users = new ORSet<>();

    // Vector clock for causal ordering
    private final VectorClock clock = new VectorClock();

    // Local message log
    private final List<Message> messageLog = Collections.synchronizedList(new ArrayList<>());

    // Active client subscriptions
    private final Map<String, FluxSink<Message>> clientSinks = new ConcurrentHashMap<>();

    // Message broadcaster
    private final Sinks.Many<Message> messageSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Flux<Message> messageFlux = messageSink.asFlux();

    public TopicHandler(String topicName, String serverId) {
        this.topicName = topicName;
        this.serverId = serverId;

        // Subscribe to internal message flux to update the log
        messageFlux.subscribe(this::addToLog);
    }

    /**
     * Adds a peer server to this topic
     */
    public void addPeerServer(SCAddress peerServer) {
        if (!peerServers.contains(peerServer)) {
            peerServers.add(peerServer);
            logger.info("Added peer server {} for topic {}", peerServer, topicName);
        }
    }

    /**
     * Gets all peer servers for this topic
     */
    public List<SCAddress> getPeerServers() {
        return new ArrayList<>(peerServers);
    }

    /**
     * Registers a user in this topic
     */
    public void addUser(String userId) {
        users.add(userId);
        logger.info("User {} joined topic {}", userId, topicName);
    }

    /**
     * Removes a user from this topic
     */
    public void removeUser(String userId) {
        users.remove(userId);
        clientSinks.remove(userId);
        logger.info("User {} left topic {}", userId, topicName);
    }

    /**
     * Gets the current set of users in this topic
     */
    public Set<String> getUsers() {
        return users.elements();
    }

    /**
     * Merges user lists with another server
     */
    public void mergeUsers(ORSet<String> otherUsers) {
        users.merge(otherUsers);
    }

    /**
     * Handles a new message in this topic
     */
    public void handleMessage(Message message) {
        // Increment vector clock for this server
        clock.increment(serverId);

        // Deliver message to local subscribers
        messageSink.tryEmitNext(message);
    }

    /**
     * Adds a message to the log
     */
    private void addToLog(Message message) {
        synchronized (messageLog) {
            messageLog.add(message);
            // Sort messages by causal order
            sortMessageLog();
        }
    }

    /**
     * Sorts the message log based on causal ordering
     */
    private void sortMessageLog() {
        // Simple implementation: Sort by timestamp
        // A more sophisticated implementation would use vector clocks
        messageLog.sort(Comparator.comparing(Message::getTimestamp));
    }

    /**
     * Gets the message log for this topic
     */
    public List<Message> getMessageLog() {
        synchronized (messageLog) {
            return new ArrayList<>(messageLog);
        }
    }

    /**
     * Gets the message log filtered by user
     */
    public List<Message> getMessageLogByUser(String userId) {
        synchronized (messageLog) {
            return messageLog.stream()
                    .filter(msg -> msg.getUserId().equals(userId))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /**
     * Subscribes a client to messages in this topic
     */
    public Flux<Message> subscribe(String userId) {
        return messageFlux
                .doOnCancel(() -> clientSinks.remove(userId))
                .doOnTerminate(() -> clientSinks.remove(userId));
    }

    /**
     * Gets the current vector clock
     */
    public VectorClock getVectorClock() {
        return clock;
    }

    /**
     * Gets the topic name
     */
    public String getTopicName() {
        return topicName;
    }
}
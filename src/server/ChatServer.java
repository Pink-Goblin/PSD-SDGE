package com.chatapp.server;

import com.chatapp.grpc.*;
import com.chatapp.model.Message;
import com.chatapp.model.VectorClock;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ChatServer {
    private static final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    // Server identity
    private final String serverId;
    private final String host;
    private final int port;

    // Topic management
    private final Map<String, TopicHandler> topics = new ConcurrentHashMap<>();

    // Server metrics for load balancing
    private final AtomicInteger clientCount = new AtomicInteger(0);

    // SC-to-SC communication for each topic
    private final PeerCommunicator peerCommunicator;

    // Log storage
    private final LogManager logManager;

    // gRPC server for client connections
    private Server grpcServer;

    public ChatServer(String host, int port, String logDir) {
        this.serverId = host + ":" + port;
        this.host = host;
        this.port = port;
        this.logManager = new LogManager(logDir);
        this.peerCommunicator = new PeerCommunicator(this);
    }

    /**
     * Starts the chat server
     */
    public void start() throws IOException {
        // Start the gRPC server
        grpcServer = ServerBuilder.forPort(port)
                .addService(new ChatServiceImpl())
                .build()
                .start();

        logger.info("Chat server started on port {}", port);

        // Start the peer communicator
        peerCommunicator.start();

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down chat server...");
            if (grpcServer != null) {
                grpcServer.shutdown();
            }
            peerCommunicator.stop();
        }));
    }

    /**
     * Stops the chat server
     */
    public void stop() {
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
        peerCommunicator.stop();
    }

    /**
     * Creates or gets a topic handler
     */
    public TopicHandler getOrCreateTopic(String topicName) {
        return topics.computeIfAbsent(topicName, name -> {
            TopicHandler handler = new TopicHandler(name, serverId);
            logger.info("Created new topic handler for {}", name);
            return handler;
        });
    }

    /**
     * Gets the number of active clients
     */
    public int getClientCount() {
        return clientCount.get();
    }

    /**
     * Gets the number of topics
     */
    public int getTopicCount() {
        return topics.size();
    }

    /**
     * Gets the server ID
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Gets the host address
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port
     */
    public int getPort() {
        return port;
    }

    /**
     * gRPC service implementation
     */
    private class ChatServiceImpl extends ReactorChatServiceGrpc.ChatServiceImplBase {
        @Override
        public Flux<ChatMessage> joinChat(Mono<JoinRequest> request) {
            return request.flatMapMany(req -> {
                String userId = req.getUserId();
                String topicName = req.getTopic();

                // Get or create topic
                TopicHandler topicHandler = getOrCreateTopic(topicName);

                // Add user to topic
                topicHandler.addUser(userId);

                // Increment client count
                clientCount.incrementAndGet();

                // Subscribe to messages
                return topicHandler.subscribe(userId)
                        .map(this::convertToGrpcMessage)
                        .doFinally(signal -> {
                            // Cleanup when client disconnects
                            topicHandler.removeUser(userId);
                            clientCount.decrementAndGet();
                        });
            });
        }

        @Override
        public Mono<SendConfirmation> sendMessage(Mono<ChatMessage> request) {
            return request.flatMap(req -> {
                // Convert to internal message
                Message message = convertFromGrpcMessage(req);

                // Get topic handler
                TopicHandler topicHandler = getOrCreateTopic(message.getTopic());

                // Handle message
                topicHandler.handleMessage(message);
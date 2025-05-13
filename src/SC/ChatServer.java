import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatServer {
    private final String address; // e.g., "tcp://localhost:5556"
    private final ZContext context;
    private final Socket publisher;
    private final Socket subscriber;
    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>(); // Topic to GroupInfo
    private final Map<String, String> clientTopics = new ConcurrentHashMap<>(); // ClientID to Topic
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ChatServer(String address) {
        this.address = address;
        this.context = new ZContext();
        this.publisher = context.createSocket(ZMQ.PUB);
        this.publisher.bind(address);
        this.subscriber = context.createSocket(ZMQ.SUB);
        this.subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
    }

    // Initialize state for a new topic
    public void onNewTopic(String topic, List<String> otherSCs) {
        GroupInfo groupInfo = new GroupInfo(topic, otherSCs);
        groups.put(topic, groupInfo);
        for (String sc : otherSCs) {
            subscriber.connect(sc);
        }
    }

    // Handle client connection
    public void onClientConnect(String clientID, String topic) {
        GroupInfo groupInfo = groups.get(topic);
        if (groupInfo != null) {
            groupInfo.userSet.add(clientID);
            clientTopics.put(clientID, topic);
        }
    }

    // Handle client disconnection
    public void onClientDisconnect(String clientID) {
        String topic = clientTopics.remove(clientID);
        if (topic != null) {
            GroupInfo groupInfo = groups.get(topic);
            if (groupInfo != null) {
                groupInfo.userSet.remove(clientID);
            }
        }
    }

    // Send message to the group
    public void sendMessage(String topic, String message, String clientID) {
        GroupInfo groupInfo = groups.get(topic);
        if (groupInfo != null) {
            int myIndex = groupInfo.getMyIndex();
            groupInfo.vectorClock[myIndex]++;
            String vcStr = vectorClockToString(groupInfo.vectorClock);
            String msg = "MSG:" + topic + ":" + clientID + ":" + message + ":" + vcStr;
            publisher.send(msg);
            // Deliver to local clients immediately
            deliverMessage(topic, clientID, message, groupInfo.vectorClock.clone());
        }
    }

    // Receive and process messages from other SCs
    private void receiveMessages() {
        while (true) {
            String msg = subscriber.recvStr();
            if (msg.startsWith("MSG:")) {
                String[] parts = msg.substring(4).split(":", 5);
                String topic = parts[0];
                String clientID = parts[1];
                String message = parts[2];
                int[] vc = stringToVectorClock(parts[3]);
                GroupInfo groupInfo = groups.get(topic);
                if (groupInfo != null) {
                    if (canDeliver(vc, groupInfo.vectorClock)) {
                        deliverMessage(topic, clientID, message, vc);
                        Iterator<Message> iter = groupInfo.buffer.iterator();
                        while (iter.hasNext()) {
                            Message bufferedMsg = iter.next();
                            if (canDeliver(bufferedMsg.vc, groupInfo.vectorClock)) {
                                deliverMessage(topic, bufferedMsg.clientID, bufferedMsg.message, bufferedMsg.vc);
                                iter.remove();
                            }
                        }
                    } else {
                        groupInfo.buffer.add(new Message(clientID, message, vc));
                    }
                }
            }
        }
    }

    private boolean canDeliver(int[] msgVC, int[] localVC) {
        for (int i = 0; i < msgVC.length; i++) {
            if (msgVC[i] > localVC[i] + 1) return false;
        }
        return true;
    }

    private void deliverMessage(String topic, String clientID, String message, int[] vc) {
        GroupInfo groupInfo = groups.get(topic);
        if (groupInfo != null) {
            for (int i = 0; i < vc.length; i++) {
                groupInfo.vectorClock[i] = Math.max(groupInfo.vectorClock[i], vc[i]);
            }
            groupInfo.log.add(new Message(clientID, message, vc));
            // In a real system, notify connected clients here
        }
    }

    // Periodic synchronization
    private void synchronize() {
        for (GroupInfo groupInfo : groups.values()) {
            String userSetStr = groupInfo.userSet.toString();
            publisher.send("USERS:" + groupInfo.topic + ":" + userSetStr);
            // Log synchronization can be added similarly
        }
    }

    // gRPC service implementations
    public void sendMessageGrpc(String topic, String message, String clientID) {
        sendMessage(topic, message, clientID);
    }

    public List<Message> getLog(String topic) {
        GroupInfo groupInfo = groups.get(topic);
        return groupInfo != null ? groupInfo.log : Collections.emptyList();
    }

    public Set<String> getUsers(String topic) {
        GroupInfo groupInfo = groups.get(topic);
        return groupInfo != null ? groupInfo.userSet.getUsers() : Collections.emptySet();
    }

    // Helper methods
    private String vectorClockToString(int[] vc) {
        return Arrays.toString(vc);
    }

    private int[] stringToVectorClock(String s) {
        String[] parts = s.substring(1, s.length() - 1).split(", ");
        int[] vc = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vc[i] = Integer.parseInt(parts[i]);
        }
        return vc;
    }

    // Inner classes
    private class GroupInfo {
        String topic;
        List<String> otherSCs;
        int[] vectorClock;
        List<Message> log = new ArrayList<>();
        ORSet userSet = new ORSet();
        List<Message> buffer = new ArrayList<>();

        GroupInfo(String topic, List<String> otherSCs) {
            this.topic = topic;
            this.otherSCs = otherSCs;
            this.vectorClock = new int[otherSCs.size() + 1]; // Including self
        }

        int getMyIndex() {
            return 0; // Self at index 0
        }
    }

    private class Message {
        String clientID;
        String message;
        int[] vc;

        Message(String clientID, String message, int[] vc) {
            this.clientID = clientID;
            this.message = message;
            this.vc = vc;
        }
    }

    private class ORSet {
        private final Map<String, Set<String>> added = new HashMap<>();
        private final Map<String, Set<String>> removed = new HashMap<>();

        public void add(String user) {
            String tag = UUID.randomUUID().toString();
            added.computeIfAbsent(user, k -> new HashSet<>()).add(tag);
        }

        public void remove(String user) {
            Set<String> tags = added.get(user);
            if (tags != null) {
                removed.computeIfAbsent(user, k -> new HashSet<>()).addAll(tags);
            }
        }

        public Set<String> getUsers() {
            Set<String> users = new HashSet<>();
            for (String user : added.keySet()) {
                Set<String> tags = added.get(user);
                Set<String> remTags = removed.getOrDefault(user, Collections.emptySet());
                if (!tags.stream().allMatch(remTags::contains)) {
                    users.add(user);
                }
            }
            return users;
        }

        public void merge(ORSet other) {
            for (String user : other.added.keySet()) {
                added.computeIfAbsent(user, k -> new HashSet<>()).addAll(other.added.get(user));
            }
            for (String user : other.removed.keySet()) {
                removed.computeIfAbsent(user, k -> new HashSet<>()).addAll(other.removed.get(user));
            }
        }

        @Override
        public String toString() {
            return getUsers().toString();
        }
    }

    public static void main(String[] args) throws Exception {
        ChatServer sc = new ChatServer("tcp://*:5556");
        Server server = ServerBuilder.forPort(50051)
                .addService(new ChatServiceImpl(sc))
                .build();
        server.start();
        new Thread(sc::receiveMessages).start();
        sc.scheduler.scheduleAtFixedRate(sc::synchronize, 5, 5, TimeUnit.SECONDS);
        server.awaitTermination();
    }
}

class ChatServiceImpl extends ChatServiceGrpc.ChatServiceImplBase {
    private final ChatServer sc;

    ChatServiceImpl(ChatServer sc) {
        this.sc = sc;
    }

    @Override
    public void sendMessage(SendMessageRequest req, StreamObserver<Empty> responseObserver) {
        sc.sendMessageGrpc(req.getTopic(), req.getMessage(), req.getClientId());
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getLog(GetLogRequest req, StreamObserver<Message> responseObserver) {
        List<ChatServer.Message> log = sc.getLog(req.getTopic());
        for (ChatServer.Message msg : log) {
            responseObserver.onNext(Message.newBuilder()
                    .setClientId(msg.clientID)
                    .setMessage(msg.message)
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getUsers(GetUsersRequest req, StreamObserver<UsersResponse> responseObserver) {
        Set<String> users = sc.getUsers(req.getTopic());
        responseObserver.onNext(UsersResponse.newBuilder().addAllUsers(users).build());
        responseObserver.onCompleted();
    }
}
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.*;
import java.util.*;

public class ChatClient {
    private final String clientID;
    private final String spAddress; // e.g., "tcp://localhost:4000"
    private final String saAddress; // e.g., "tcp://localhost:5555"
    private final ZContext context;
    private final Socket saSocket; // ZeroMQ socket to SA
    private ManagedChannel channel;
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;
    private ChatServiceGrpc.ChatServiceStub asyncStub;
    private String currentTopic;
    private List<String> scAddresses;

    public ChatClient(String clientID, String spAddress, String saAddress) {
        this.clientID = clientID;
        this.spAddress = spAddress;
        this.saAddress = saAddress;
        this.context = new ZContext();
        this.saSocket = context.createSocket(ZMQ.REQ);
        this.saSocket.connect(saAddress);
    }

    // Search for a topic or create a new one
    public void joinOrCreateGroup(String topic) {
        scAddresses = searchTopic(topic);
        if (scAddresses.isEmpty()) {
            scAddresses = createGroup(topic);
        }
        if (!scAddresses.isEmpty()) {
            connectToSC(scAddresses.get(0));
            currentTopic = topic;
        }
    }

    private List<String> searchTopic(String topic) {
        try (Socket socket = new Socket(spAddress.split(":")[1].substring(2), Integer.parseInt(spAddress.split(":")[2]));
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.writeObject("get");
            out.writeObject(topic);
            Object response = in.readObject();
            if (response instanceof List) {
                return (List<String>) response;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    private List<String> createGroup(String topic) {
        saSocket.send("CREATE:" + topic);
        String response = saSocket.recvStr();
        if (response.startsWith("SCS:")) {
            return Arrays.asList(response.substring(4).split(","));
        }
        return Collections.emptyList();
    }

    private void connectToSC(String scAddress) {
        channel = ManagedChannelBuilder.forTarget(scAddress).usePlaintext().build();
        blockingStub = ChatServiceGrpc.newBlockingStub(channel);
        asyncStub = ChatServiceGrpc.newStub(channel);
        // Start listening for messages
        asyncStub.streamMessages(StreamMessagesRequest.newBuilder().setTopic(currentTopic).build(),
                new StreamObserver<Message>() {
                    @Override
                    public void onNext(Message value) {
                        System.out.println(value.getClientId() + ": " + value.getMessage());
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
    }

    public void sendMessage(String message) {
        if (currentTopic != null) {
            blockingStub.sendMessage(SendMessageRequest.newBuilder()
                    .setTopic(currentTopic)
                    .setMessage(message)
                    .setClientId(clientID)
                    .build());
        }
    }

    public List<Message> getLog() {
        if (currentTopic != null) {
            return blockingStub.getLog(GetLogRequest.newBuilder().setTopic(currentTopic).build()).getMessagesList();
        }
        return Collections.emptyList();
    }

    public Set<String> getUsers() {
        if (currentTopic != null) {
            return new HashSet<>(blockingStub.getUsers(GetUsersRequest.newBuilder().setTopic(currentTopic).build()).getUsersList());
        }
        return Collections.emptySet();
    }

    public static void main(String[] args) {
        ChatClient client = new ChatClient("client1", "tcp://localhost:4000", "tcp://localhost:5555");
        client.joinOrCreateGroup("gaming");
        client.sendMessage("Hello, world!");
        System.out.println("Users: " + client.getUsers());
        System.out.println("Log: " + client.getLog());
    }
}
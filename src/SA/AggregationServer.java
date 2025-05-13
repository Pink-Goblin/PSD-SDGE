import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.util.*;

public class AggregationServer {
    private final String address; // Address of this SA (e.g., "tcp://localhost:5555")
    private final String scAddress; // Address of the associated SC (e.g., "tcp://localhost:5556")
    private final List<String> neighbors; // List of neighbor SA addresses
    private final ZContext context;
    private final Socket publisher;
    private final Socket subscriber;
    private final Socket spSocket; // Socket to communicate with SP

    private int scClientCount = 0; // Number of clients connected to the associated SC
    private int scTopicCount = 0; // Number of topics served by the associated SC

    private final int C = 2; // Number of SCs to select for a new group
    private final int ROUNDS = 5; // Number of gossip rounds

    public AggregationServer(String address, String scAddress, List<String> neighbors, String spAddress) {
        this.address = address;
        this.scAddress = scAddress;
        this.neighbors = neighbors;
        this.context = new ZContext();
        this.publisher = context.createSocket(ZMQ.PUB);
        this.publisher.bind(address);
        this.subscriber = context.createSocket(ZMQ.SUB);
        for (String neighbor : neighbors) {
            this.subscriber.connect(neighbor);
        }
        this.subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
        this.spSocket = context.createSocket(ZMQ.REQ);
        this.spSocket.connect(spAddress);
    }

    // Simulate updating SC load information
    public void updateSCLoad(int clientCount, int topicCount) {
        this.scClientCount = clientCount;
        this.scTopicCount = topicCount;
    }

    // Handle group creation request
    public void createGroup(String topic) {
        // Start gossip to find C least-loaded SCs
        List<SCInfo> selectedSCs = gossipAggregation();
        // Store in SP
        storeInSP(topic, selectedSCs);
        // Notify selected SCs
        notifySCs(selectedSCs, topic);
    }

    private List<SCInfo> gossipAggregation() {
        // Initialize with own SC info
        List<SCInfo> knownSCs = new ArrayList<>();
        knownSCs.add(new SCInfo(scAddress, scClientCount, scTopicCount));

        for (int round = 0; round < ROUNDS; round++) {
            // Send current known SCs to neighbors
            String message = serializeSCList(knownSCs);
            publisher.send(message);

            // Receive from neighbors
            List<SCInfo> receivedSCs = new ArrayList<>();
            while (true) {
                String reply = subscriber.recvStr(ZMQ.DONTWAIT);
                if (reply == null) break;
                receivedSCs.addAll(deserializeSCList(reply));
            }

            // Merge and select top C
            knownSCs.addAll(receivedSCs);
            knownSCs = selectLeastLoaded(knownSCs, C);
        }

        return knownSCs;
    }

    private List<SCInfo> selectLeastLoaded(List<SCInfo> scList, int count) {
        scList.sort((a, b) -> {
            if (a.clientCount != b.clientCount) return a.clientCount - b.clientCount;
            return a.topicCount - b.topicCount;
        });
        return scList.subList(0, Math.min(count, scList.size()));
    }

    private void storeInSP(String topic, List<SCInfo> scs) {
        // Serialize topic and SC addresses
        String message = topic + ":" + scs.stream().map(sc -> sc.address).reduce((a, b) -> a + "," + b).orElse("");
        spSocket.send(message);
        spSocket.recvStr(); // Wait for acknowledgment
    }

    private void notifySCs(List<SCInfo> scs, String topic) {
        for (SCInfo sc : scs) {
            // Assuming SCs are listening on their addresses
            Socket scSocket = context.createSocket(ZMQ.REQ);
            scSocket.connect(sc.address);
            scSocket.send("NEW_TOPIC:" + topic);
            scSocket.recvStr(); // Wait for acknowledgment
            scSocket.close();
        }
    }

    // Helper methods for serialization
    private String serializeSCList(List<SCInfo> scList) {
        // Simple CSV format: address,clientCount,topicCount;...
        return scList.stream()
                .map(sc -> sc.address + "," + sc.clientCount + "," + sc.topicCount)
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private List<SCInfo> deserializeSCList(String message) {
        List<SCInfo> scList = new ArrayList<>();
        for (String scStr : message.split(";")) {
            String[] parts = scStr.split(",");
            if (parts.length == 3) {
                scList.add(new SCInfo(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        }
        return scList;
    }

    // Inner class to hold SC information
    private static class SCInfo {
        String address;
        int clientCount;
        int topicCount;

        SCInfo(String address, int clientCount, int topicCount) {
            this.address = address;
            this.clientCount = clientCount;
            this.topicCount = topicCount;
        }
    }

    public static void main(String[] args) {
        // Example usage
        List<String> neighbors = Arrays.asList("tcp://localhost:5556", "tcp://localhost:5557");
        AggregationServer sa = new AggregationServer("tcp://*:5555", "tcp://localhost:5556", neighbors, "tcp://localhost:4000");
        // Simulate updating SC load
        sa.updateSCLoad(10, 5);
        // Simulate group creation
        sa.createGroup("gaming");
    }
}
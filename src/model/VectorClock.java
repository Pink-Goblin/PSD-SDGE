package com.chatapp.model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VectorClock {
    private final Map<String, Integer> clock = new ConcurrentHashMap<>();

    public VectorClock() {
    }

    public VectorClock(Map<String, Integer> clock) {
        this.clock.putAll(clock);
    }

    public void increment(String nodeId) {
        clock.compute(nodeId, (k, v) -> (v == null) ? 1 : v + 1);
    }

    public Map<String, Integer> getClock() {
        return new HashMap<>(clock);
    }

    public void merge(VectorClock other) {
        other.clock.forEach((nodeId, count) ->
                clock.compute(nodeId, (k, v) -> (v == null || v < count) ? count : v));
    }

    /**
     * Checks if this clock happens before another clock
     * @param other The other vector clock
     * @return true if this clock happens before other, false otherwise
     */
    public boolean happensBefore(VectorClock other) {
        boolean lessThanInSome = false;

        for (Map.Entry<String, Integer> entry : other.clock.entrySet()) {
            String nodeId = entry.getKey();
            Integer otherValue = entry.getValue();
            Integer thisValue = clock.getOrDefault(nodeId, 0);

            if (thisValue > otherValue) {
                return false; // Not "happens before" if any entry is greater
            }

            if (thisValue < otherValue) {
                lessThanInSome = true;
            }
        }

        // All entries are less than or equal, and at least one is less
        return lessThanInSome;
    }

    /**
     * Checks if this clock is concurrent with another clock
     * @param other The other vector clock
     * @return true if the clocks are concurrent, false otherwise
     */
    public boolean isConcurrentWith(VectorClock other) {
        return !this.happensBefore(other) && !other.happensBefore(this);
    }

    @Override
    public String toString() {
        return clock.toString();
    }
}
package com.chatapp.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Observed-Remove Set CRDT implementation for user list synchronization
 * @param <T> Type of elements in the set
 */
public class ORSet<T> {
    private final Map<T, Set<UUID>> addSet = new ConcurrentHashMap<>();
    private final Map<T, Set<UUID>> removeSet = new ConcurrentHashMap<>();

    public void add(T element) {
        UUID uniqueTag = UUID.randomUUID();
        addSet.computeIfAbsent(element, k -> new HashSet<>()).add(uniqueTag);
    }

    public void remove(T element) {
        Set<UUID> tags = addSet.get(element);
        if (tags != null) {
            removeSet.computeIfAbsent(element, k -> new HashSet<>()).addAll(tags);
        }
    }

    public boolean contains(T element) {
        Set<UUID> addTags = addSet.getOrDefault(element, Collections.emptySet());
        Set<UUID> removeTags = removeSet.getOrDefault(element, Collections.emptySet());

        // Element exists if there are add tags not covered by remove tags
        return addTags.stream().anyMatch(tag -> !removeTags.contains(tag));
    }

    public Set<T> elements() {
        return addSet.entrySet().stream()
                .filter(e -> {
                    Set<UUID> removeTags = removeSet.getOrDefault(e.getKey(), Collections.emptySet());
                    // Keep element if not all its add-tags are in remove-tags
                    return e.getValue().stream().anyMatch(tag -> !removeTags.contains(tag));
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void merge(ORSet<T> other) {
        // Merge add sets
        other.addSet.forEach((element, tags) -> {
            addSet.computeIfAbsent(element, k -> new HashSet<>()).addAll(tags);
        });

        // Merge remove sets
        other.removeSet.forEach((element, tags) -> {
            removeSet.computeIfAbsent(element, k -> new HashSet<>()).addAll(tags);
        });
    }
}
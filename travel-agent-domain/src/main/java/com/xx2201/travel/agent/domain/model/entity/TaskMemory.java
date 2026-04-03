package com.xx2201.travel.agent.domain.model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record TaskMemory(
        String conversationId,
        String origin,
        String destination,
        Integer days,
        String budget,
        List<String> preferences,
        String pendingQuestion,
        String summary,
        Instant updatedAt
) {

    public TaskMemory {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
    }

    public static TaskMemory empty(String conversationId) {
        return new TaskMemory(conversationId, null, null, null, null, List.of(), null, null, Instant.now());
    }

    public TaskMemory merge(TaskMemory candidate) {
        if (candidate == null) {
            return this;
        }
        return new TaskMemory(
                conversationId,
                chooseText(candidate.origin, origin),
                chooseText(candidate.destination, destination),
                candidate.days != null ? candidate.days : days,
                chooseText(candidate.budget, budget),
                mergePreferences(preferences, candidate.preferences),
                chooseText(candidate.pendingQuestion, pendingQuestion),
                chooseText(candidate.summary, summary),
                Instant.now()
        );
    }

    private static String chooseText(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static List<String> mergePreferences(List<String> current, List<String> incoming) {
        Set<String> merged = new LinkedHashSet<>();
        if (current != null) {
            merged.addAll(current);
        }
        if (incoming != null) {
            merged.addAll(incoming.stream().filter(TaskMemory::hasText).map(String::trim).toList());
        }
        return new ArrayList<>(merged);
    }
}

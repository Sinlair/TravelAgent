package com.travalagent.domain.model.entity;

import com.travalagent.domain.model.valobj.AgentType;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ConversationFeedback(
        String conversationId,
        String label,
        String targetId,
        String targetScope,
        String planVersion,
        List<String> reasonLabels,
        String reasonCode,
        String note,
        AgentType agentType,
        String destination,
        Integer days,
        String budget,
        boolean hasTravelPlan,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {

    public ConversationFeedback {
        reasonLabels = reasonLabels == null ? List.of() : List.copyOf(reasonLabels);
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}

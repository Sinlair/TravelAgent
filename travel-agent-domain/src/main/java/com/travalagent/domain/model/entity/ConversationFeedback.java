package com.travalagent.domain.model.entity;

import com.travalagent.domain.model.valobj.AgentType;

import java.time.Instant;
import java.util.Map;

public record ConversationFeedback(
        String conversationId,
        String label,
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
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}

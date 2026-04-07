package com.travalagent.domain.model.entity;

import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.MessageRole;

import java.time.Instant;
import java.util.Map;

public record ConversationMessage(
        String id,
        String conversationId,
        MessageRole role,
        String content,
        AgentType agentType,
        Instant createdAt,
        Map<String, Object> metadata
) {

    public ConversationMessage {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ConversationMessage(
            String id,
            String conversationId,
            MessageRole role,
            String content,
            AgentType agentType,
            Instant createdAt
    ) {
        this(id, conversationId, role, content, agentType, createdAt, Map.of());
    }
}

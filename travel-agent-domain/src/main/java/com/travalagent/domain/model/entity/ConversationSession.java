package com.travalagent.domain.model.entity;

import com.travalagent.domain.model.valobj.AgentType;

import java.time.Instant;

public record ConversationSession(
        String conversationId,
        String title,
        AgentType lastAgent,
        String summary,
        Instant createdAt,
        Instant updatedAt
) {
}

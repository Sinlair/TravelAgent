package com.xx2201.travel.agent.domain.model.entity;

import com.xx2201.travel.agent.domain.model.valobj.AgentType;

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

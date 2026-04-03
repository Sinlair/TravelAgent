package com.xx2201.travel.agent.domain.model.entity;

import com.xx2201.travel.agent.domain.model.valobj.AgentType;
import com.xx2201.travel.agent.domain.model.valobj.MessageRole;

import java.time.Instant;

public record ConversationMessage(
        String id,
        String conversationId,
        MessageRole role,
        String content,
        AgentType agentType,
        Instant createdAt
) {
}

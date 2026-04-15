package com.travalagent.app.dto;

import com.travalagent.domain.model.valobj.AgentType;

import java.util.List;

public record ChatResponseFeedbackTarget(
        String targetId,
        String conversationId,
        String scope,
        String planVersion,
        AgentType agentType,
        boolean hasTravelPlan,
        List<String> availableScopes
) {
    public ChatResponseFeedbackTarget {
        availableScopes = availableScopes == null ? List.of() : List.copyOf(availableScopes);
    }
}

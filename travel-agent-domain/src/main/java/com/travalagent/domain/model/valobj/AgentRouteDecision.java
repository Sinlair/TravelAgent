package com.travalagent.domain.model.valobj;

public record AgentRouteDecision(
        AgentType agentType,
        String reason,
        boolean clarificationRequired,
        String clarificationQuestion
) {
}

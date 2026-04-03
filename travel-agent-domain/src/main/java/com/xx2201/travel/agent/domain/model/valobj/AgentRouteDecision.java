package com.xx2201.travel.agent.domain.model.valobj;

public record AgentRouteDecision(
        AgentType agentType,
        String reason,
        boolean clarificationRequired,
        String clarificationQuestion
) {
}

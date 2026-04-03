package com.xx2201.travel.agent.domain.model.valobj;

import com.xx2201.travel.agent.domain.model.entity.TravelPlan;

import java.util.Map;

public record AgentExecutionResult(
        AgentType agentType,
        String answer,
        Map<String, Object> metadata,
        TravelPlan travelPlan
) {
}


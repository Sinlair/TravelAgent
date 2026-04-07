package com.travalagent.domain.model.valobj;

import com.travalagent.domain.model.entity.TravelPlan;

import java.util.Map;

public record AgentExecutionResult(
        AgentType agentType,
        String answer,
        Map<String, Object> metadata,
        TravelPlan travelPlan
) {
}


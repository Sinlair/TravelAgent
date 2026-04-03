package com.xx2201.travel.agent.app.dto;

import com.xx2201.travel.agent.domain.model.entity.TaskMemory;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.model.entity.TravelPlan;
import com.xx2201.travel.agent.domain.model.valobj.AgentType;

import java.util.List;

public record ChatResponse(
        String conversationId,
        AgentType agentType,
        String answer,
        TaskMemory taskMemory,
        TravelPlan travelPlan,
        List<TimelineEvent> timeline
) {
}


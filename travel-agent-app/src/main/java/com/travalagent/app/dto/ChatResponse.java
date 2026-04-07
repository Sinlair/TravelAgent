package com.travalagent.app.dto;

import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.AgentType;

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


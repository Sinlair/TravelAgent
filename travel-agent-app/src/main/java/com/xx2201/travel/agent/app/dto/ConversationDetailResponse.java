package com.xx2201.travel.agent.app.dto;

import com.xx2201.travel.agent.domain.model.entity.ConversationMessage;
import com.xx2201.travel.agent.domain.model.entity.ConversationSession;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;
import com.xx2201.travel.agent.domain.model.entity.TimelineEvent;
import com.xx2201.travel.agent.domain.model.entity.TravelPlan;

import java.util.List;

public record ConversationDetailResponse(
        ConversationSession conversation,
        List<ConversationMessage> messages,
        List<TimelineEvent> timeline,
        TaskMemory taskMemory,
        TravelPlan travelPlan
) {
}


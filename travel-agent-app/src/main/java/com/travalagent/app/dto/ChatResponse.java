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
        List<TimelineEvent> timeline,
        ChatResponseFeedbackTarget feedbackTarget,
        List<ChatResponseIssue> issues,
        List<ConversationMissingInformationItem> missingInformation,
        ConversationConstraintSummary constraintSummary
) {
    public ChatResponse {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        issues = issues == null ? List.of() : List.copyOf(issues);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
        constraintSummary = constraintSummary == null ? ConversationConstraintSummary.none() : constraintSummary;
    }
}


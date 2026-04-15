package com.travalagent.app.dto;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.model.entity.ConversationImageContext;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.entity.TravelPlan;

import java.util.List;

public record ConversationDetailResponse(
        ConversationSession conversation,
        List<ConversationMessage> messages,
        List<TimelineEvent> timeline,
        TaskMemory taskMemory,
        TravelPlan travelPlan,
        ConversationFeedback feedback,
        ConversationImageContext imageContextCandidate,
        ChatResponseFeedbackTarget feedbackTarget,
        List<ChatResponseIssue> issues,
        List<ConversationMissingInformationItem> missingInformation,
        ConversationConstraintSummary constraintSummary
) {
    public ConversationDetailResponse {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        issues = issues == null ? List.of() : List.copyOf(issues);
        missingInformation = missingInformation == null ? List.of() : List.copyOf(missingInformation);
        constraintSummary = constraintSummary == null ? ConversationConstraintSummary.none() : constraintSummary;
    }
}


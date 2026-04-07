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
        ConversationImageContext imageContextCandidate
) {
}


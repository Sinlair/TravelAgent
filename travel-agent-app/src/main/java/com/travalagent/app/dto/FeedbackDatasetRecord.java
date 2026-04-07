package com.travalagent.app.dto;

import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelPlan;

import java.util.List;

public record FeedbackDatasetRecord(
        ConversationSession conversation,
        ConversationFeedback feedback,
        TaskMemory taskMemory,
        TravelPlan travelPlan,
        List<ConversationMessage> messages
) {
}

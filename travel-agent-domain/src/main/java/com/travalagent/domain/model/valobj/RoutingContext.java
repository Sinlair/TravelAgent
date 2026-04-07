package com.travalagent.domain.model.valobj;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.TaskMemory;

import java.util.List;

public record RoutingContext(
        String conversationId,
        String userMessage,
        List<ConversationMessage> recentMessages,
        TaskMemory taskMemory,
        String conversationSummary,
        List<LongTermMemoryItem> longTermMemories
) {
}

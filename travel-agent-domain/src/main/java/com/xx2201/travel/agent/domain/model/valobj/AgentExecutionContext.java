package com.xx2201.travel.agent.domain.model.valobj;

import com.xx2201.travel.agent.domain.model.entity.ConversationMessage;
import com.xx2201.travel.agent.domain.model.entity.TaskMemory;

import java.util.List;

public record AgentExecutionContext(
        String conversationId,
        String userMessage,
        List<ConversationMessage> recentMessages,
        TaskMemory taskMemory,
        String conversationSummary,
        List<LongTermMemoryItem> longTermMemories,
        String routeReason
) {
}

package com.travalagent.domain.model.valobj;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.TaskMemory;

import java.util.List;

public record AgentExecutionContext(
        String conversationId,
        String userMessage,
        List<ConversationMessage> recentMessages,
        TaskMemory taskMemory,
        String conversationSummary,
        List<LongTermMemoryItem> longTermMemories,
        String routeReason,
        List<ImageAttachment> imageAttachments,
        String imageContextSummary
) {

    public AgentExecutionContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        longTermMemories = longTermMemories == null ? List.of() : List.copyOf(longTermMemories);
        imageAttachments = imageAttachments == null ? List.of() : List.copyOf(imageAttachments);
    }

    public AgentExecutionContext(
            String conversationId,
            String userMessage,
            List<ConversationMessage> recentMessages,
            TaskMemory taskMemory,
            String conversationSummary,
            List<LongTermMemoryItem> longTermMemories,
            String routeReason
    ) {
        this(conversationId, userMessage, recentMessages, taskMemory, conversationSummary, longTermMemories, routeReason, List.of(), null);
    }
}

package com.travalagent.domain.model.entity;

import java.time.Instant;
import java.util.List;

public record ConversationImageContext(
        String conversationId,
        String summary,
        ConversationImageFacts facts,
        List<ConversationImageAttachment> attachments,
        Instant createdAt,
        Instant updatedAt
) {

    public ConversationImageContext {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}

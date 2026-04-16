package com.travalagent.app.dto;

import jakarta.validation.Valid;

import java.util.List;

public record ChatRequest(
        String conversationId,
        String message,
        TripBriefRequest brief,
        List<@Valid ChatImageAttachmentRequest> attachments,
        String imageContextAction,
        ReplanScopeRequest replanScope
) {

    public ChatRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public ChatRequest(
            String conversationId,
            String message,
            TripBriefRequest brief,
            List<@Valid ChatImageAttachmentRequest> attachments,
            String imageContextAction
    ) {
        this(conversationId, message, brief, attachments, imageContextAction, null);
    }
}

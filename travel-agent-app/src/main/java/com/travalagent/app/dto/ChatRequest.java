package com.travalagent.app.dto;

import jakarta.validation.Valid;

import java.util.List;

public record ChatRequest(
        String conversationId,
        String message,
        List<@Valid ChatImageAttachmentRequest> attachments,
        String imageContextAction
) {

    public ChatRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}

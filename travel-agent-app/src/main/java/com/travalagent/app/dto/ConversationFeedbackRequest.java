package com.travalagent.app.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ConversationFeedbackRequest(
        @NotBlank(message = "label cannot be blank") String label,
        String targetId,
        String targetScope,
        String planVersion,
        List<String> reasonLabels,
        String reasonCode,
        String note
) {
    public ConversationFeedbackRequest {
        reasonLabels = reasonLabels == null ? List.of() : List.copyOf(reasonLabels);
    }
}

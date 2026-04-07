package com.travalagent.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationFeedbackRequest(
        @NotBlank(message = "label cannot be blank") String label,
        String reasonCode,
        String note
) {
}

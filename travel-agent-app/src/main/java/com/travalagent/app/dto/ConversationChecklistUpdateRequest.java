package com.travalagent.app.dto;

public record ConversationChecklistUpdateRequest(
        String itemKey,
        boolean confirmed
) {
}

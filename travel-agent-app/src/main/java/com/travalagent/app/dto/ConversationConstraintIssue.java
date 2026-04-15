package com.travalagent.app.dto;

public record ConversationConstraintIssue(
        String code,
        String severity,
        String message
) {
}

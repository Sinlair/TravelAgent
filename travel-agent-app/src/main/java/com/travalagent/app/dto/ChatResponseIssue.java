package com.travalagent.app.dto;

public record ChatResponseIssue(
        String code,
        String severity,
        String message
) {
}

package com.travalagent.app.dto;

import java.util.List;

public record ConversationConstraintSummary(
        String status,
        boolean repaired,
        boolean hasRisk,
        List<ConversationConstraintIssue> issues
) {
    public ConversationConstraintSummary {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ConversationConstraintSummary none() {
        return new ConversationConstraintSummary("NONE", false, false, List.of());
    }
}

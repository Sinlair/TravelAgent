package com.travalagent.app.dto;

public record FeedbackLoopFinding(
        String type,
        String key,
        long totalCount,
        long acceptedCount,
        long partialCount,
        long rejectedCount,
        double usableRatePct,
        String recommendation
) {
}

package com.travalagent.app.dto;

public record FeedbackBreakdownItem(
        String key,
        long totalCount,
        long acceptedCount,
        long partialCount,
        long rejectedCount,
        double acceptedRatePct,
        double usableRatePct
) {
}

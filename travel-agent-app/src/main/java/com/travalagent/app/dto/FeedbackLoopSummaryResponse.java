package com.travalagent.app.dto;

import java.time.Instant;
import java.util.List;

public record FeedbackLoopSummaryResponse(
        Instant generatedAt,
        int limitApplied,
        int sampleCount,
        long acceptedCount,
        long partialCount,
        long rejectedCount,
        double acceptedRatePct,
        double usableRatePct,
        long structuredPlanCount,
        double structuredPlanCoveragePct,
        List<FeedbackBreakdownItem> topReasonCodes,
        List<FeedbackBreakdownItem> topDestinations,
        List<FeedbackBreakdownItem> topAgentTypes,
        List<FeedbackLoopFinding> keyFindings
) {
}

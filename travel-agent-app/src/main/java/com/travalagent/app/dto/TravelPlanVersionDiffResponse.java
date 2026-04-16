package com.travalagent.app.dto;

import java.util.List;

public record TravelPlanVersionDiffResponse(
        String latestVersionId,
        String previousVersionId,
        String latestCreatedAt,
        String previousCreatedAt,
        String dateSummary,
        String hotelSummary,
        String budgetSummary,
        List<String> stopHighlights
) {
    public TravelPlanVersionDiffResponse {
        stopHighlights = stopHighlights == null ? List.of() : List.copyOf(stopHighlights);
    }
}

package com.travalagent.domain.model.entity;

import java.time.Instant;

public record TravelPlanVersionSnapshot(
        String versionId,
        String conversationId,
        String inputSummary,
        String scope,
        TravelPlan travelPlan,
        Instant createdAt
) {
}

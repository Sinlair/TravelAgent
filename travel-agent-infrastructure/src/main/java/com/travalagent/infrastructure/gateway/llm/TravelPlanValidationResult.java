package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.TravelPlan;

import java.util.List;

public record TravelPlanValidationResult(
        TravelPlan normalizedPlan,
        List<String> repairCodes,
        boolean accepted,
        int failCount,
        int warningCount
) {

    public boolean requiresRepair() {
        return !repairCodes.isEmpty();
    }
}

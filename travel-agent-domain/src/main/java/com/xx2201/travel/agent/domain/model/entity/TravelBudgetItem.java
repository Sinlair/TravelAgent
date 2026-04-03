package com.xx2201.travel.agent.domain.model.entity;

public record TravelBudgetItem(
        String category,
        Integer minAmount,
        Integer maxAmount,
        String rationale
) {
}


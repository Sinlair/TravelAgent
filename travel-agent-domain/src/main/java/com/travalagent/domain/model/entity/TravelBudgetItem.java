package com.travalagent.domain.model.entity;

public record TravelBudgetItem(
        String category,
        Integer minAmount,
        Integer maxAmount,
        String rationale
) {
}


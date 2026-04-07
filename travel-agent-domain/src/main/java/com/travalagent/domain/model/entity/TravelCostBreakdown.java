package com.travalagent.domain.model.entity;

public record TravelCostBreakdown(
        Integer ticketCost,
        Integer foodCost,
        Integer localTransitCost,
        Integer otherCost,
        String note
) {
}

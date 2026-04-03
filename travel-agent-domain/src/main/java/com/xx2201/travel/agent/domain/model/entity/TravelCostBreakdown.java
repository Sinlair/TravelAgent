package com.xx2201.travel.agent.domain.model.entity;

public record TravelCostBreakdown(
        Integer ticketCost,
        Integer foodCost,
        Integer localTransitCost,
        Integer otherCost,
        String note
) {
}

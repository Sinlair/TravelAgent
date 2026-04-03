package com.xx2201.travel.agent.domain.model.entity;

public record TravelPlanStop(
        TravelPlanSlot slot,
        String name,
        String area,
        String address,
        String longitude,
        String latitude,
        String startTime,
        String endTime,
        Integer durationMinutes,
        Integer transitMinutesFromPrevious,
        Integer estimatedCost,
        String openTime,
        String closeTime,
        String rationale,
        TravelCostBreakdown costBreakdown,
        TravelPoiMatch poiMatch,
        TravelTransitLeg routeFromPrevious
) {
}


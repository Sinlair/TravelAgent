package com.travalagent.domain.model.entity;

import java.util.List;

public record TravelPlanDay(
        Integer dayNumber,
        String date,
        String theme,
        String startTime,
        String endTime,
        Integer totalTransitMinutes,
        Integer totalActivityMinutes,
        Integer estimatedCost,
        List<TravelPlanStop> stops,
        TravelTransitLeg returnToHotel
) {

    public TravelPlanDay {
        stops = stops == null ? List.of() : List.copyOf(stops);
    }

    public TravelPlanDay(
            Integer dayNumber,
            String theme,
            String startTime,
            String endTime,
            Integer totalTransitMinutes,
            Integer totalActivityMinutes,
            Integer estimatedCost,
            List<TravelPlanStop> stops,
            TravelTransitLeg returnToHotel
    ) {
        this(dayNumber, null, theme, startTime, endTime, totalTransitMinutes, totalActivityMinutes, estimatedCost, stops, returnToHotel);
    }
}


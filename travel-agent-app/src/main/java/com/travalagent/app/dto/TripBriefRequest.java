package com.travalagent.app.dto;

import java.util.List;

public record TripBriefRequest(
        String origin,
        String destination,
        String startDate,
        String endDate,
        Integer days,
        String travelers,
        String budget,
        List<String> preferences
) {
    public TripBriefRequest {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
    }
}

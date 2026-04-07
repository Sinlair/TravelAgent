package com.travalagent.domain.model.entity;

import java.util.List;

public record TravelTransitStep(
        String mode,
        String title,
        String instruction,
        String lineName,
        String fromName,
        String toName,
        Integer durationMinutes,
        Integer distanceMeters,
        Integer stopCount,
        List<String> polyline
) {

    public TravelTransitStep {
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
    }
}

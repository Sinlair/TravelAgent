package com.xx2201.travel.agent.domain.model.entity;

import java.util.List;

public record TravelTransitLeg(
        String fromName,
        String toName,
        String mode,
        String summary,
        Integer durationMinutes,
        Integer distanceMeters,
        Integer walkingMinutes,
        Integer estimatedCost,
        List<String> lineNames,
        List<TravelTransitStep> steps,
        List<String> polyline,
        String source
) {

    public TravelTransitLeg {
        lineNames = lineNames == null ? List.of() : List.copyOf(lineNames);
        steps = steps == null ? List.of() : List.copyOf(steps);
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
    }
}

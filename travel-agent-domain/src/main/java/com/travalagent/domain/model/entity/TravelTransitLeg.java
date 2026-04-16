package com.travalagent.domain.model.entity;

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
        String source,
        Double confidence,
        String freshness
) {

    public TravelTransitLeg {
        lineNames = lineNames == null ? List.of() : List.copyOf(lineNames);
        steps = steps == null ? List.of() : List.copyOf(steps);
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
    }

    public TravelTransitLeg(
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
        this(fromName, toName, mode, summary, durationMinutes, distanceMeters, walkingMinutes, estimatedCost, lineNames, steps, polyline, source, null, null);
    }
}

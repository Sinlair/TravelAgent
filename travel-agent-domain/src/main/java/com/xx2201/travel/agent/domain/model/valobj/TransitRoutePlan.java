package com.xx2201.travel.agent.domain.model.valobj;

import java.util.List;

public record TransitRoutePlan(
        String mode,
        String summary,
        Integer durationMinutes,
        Integer distanceMeters,
        Integer walkingMinutes,
        Integer cost,
        List<String> lineNames,
        List<TransitRouteStep> steps,
        List<String> polyline
) {

    public TransitRoutePlan {
        lineNames = lineNames == null ? List.of() : List.copyOf(lineNames);
        steps = steps == null ? List.of() : List.copyOf(steps);
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
    }
}

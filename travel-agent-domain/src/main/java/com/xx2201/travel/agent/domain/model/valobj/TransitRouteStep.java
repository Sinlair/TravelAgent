package com.xx2201.travel.agent.domain.model.valobj;

import java.util.List;

public record TransitRouteStep(
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

    public TransitRouteStep {
        polyline = polyline == null ? List.of() : List.copyOf(polyline);
    }
}

package com.travalagent.domain.model.valobj;

public record TransitRouteQuery(
        String originLongitude,
        String originLatitude,
        String destinationLongitude,
        String destinationLatitude,
        String city
) {
}

package com.xx2201.travel.agent.domain.model.valobj;

public record TransitRouteQuery(
        String originLongitude,
        String originLatitude,
        String destinationLongitude,
        String destinationLatitude,
        String city
) {
}

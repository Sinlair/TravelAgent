package com.xx2201.travel.agent.domain.model.valobj;

public record PlaceSearchQuery(
        String keyword,
        String city,
        String type,
        String location,
        boolean cityLimit,
        String dataType
) {
}

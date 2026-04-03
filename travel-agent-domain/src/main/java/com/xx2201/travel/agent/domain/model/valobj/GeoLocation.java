package com.xx2201.travel.agent.domain.model.valobj;

public record GeoLocation(
        String name,
        String address,
        String longitude,
        String latitude,
        String adCode
) {
}

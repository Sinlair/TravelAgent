package com.travalagent.domain.model.valobj;

public record GeoLocation(
        String name,
        String address,
        String longitude,
        String latitude,
        String adCode
) {
}

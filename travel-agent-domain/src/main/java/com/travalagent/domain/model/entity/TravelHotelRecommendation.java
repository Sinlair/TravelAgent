package com.travalagent.domain.model.entity;

public record TravelHotelRecommendation(
        String name,
        String area,
        String address,
        Integer nightlyMin,
        Integer nightlyMax,
        String rationale,
        String longitude,
        String latitude,
        String source,
        String bookingUrl,
        Double confidence,
        String freshness
) {
    public TravelHotelRecommendation(
            String name,
            String area,
            String address,
            Integer nightlyMin,
            Integer nightlyMax,
            String rationale,
            String longitude,
            String latitude,
            String source,
            String bookingUrl
    ) {
        this(name, area, address, nightlyMin, nightlyMax, rationale, longitude, latitude, source, bookingUrl, null, null);
    }
}

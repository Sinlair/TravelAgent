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
        String source
) {
}

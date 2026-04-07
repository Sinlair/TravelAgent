package com.travalagent.domain.model.entity;

import java.util.List;

public record TravelPoiMatch(
        String query,
        String matchedName,
        String district,
        String address,
        String adCode,
        String longitude,
        String latitude,
        Double confidence,
        List<String> candidateNames,
        String source
) {

    public TravelPoiMatch {
        candidateNames = candidateNames == null ? List.of() : List.copyOf(candidateNames);
    }
}

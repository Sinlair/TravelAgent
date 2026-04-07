package com.travalagent.domain.model.valobj;

public record PlaceSuggestion(
        String id,
        String name,
        String district,
        String address,
        String adCode,
        String location,
        String typeCode,
        String type
) {
}

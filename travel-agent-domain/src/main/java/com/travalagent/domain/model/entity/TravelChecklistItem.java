package com.travalagent.domain.model.entity;

public record TravelChecklistItem(
        String key,
        String title,
        String details,
        boolean confirmed
) {
}

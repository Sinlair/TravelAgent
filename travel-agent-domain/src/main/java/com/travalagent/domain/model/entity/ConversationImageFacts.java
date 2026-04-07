package com.travalagent.domain.model.entity;

import java.util.List;

public record ConversationImageFacts(
        String origin,
        String destination,
        String startDate,
        String endDate,
        Integer days,
        String budget,
        String hotelName,
        String hotelArea,
        List<String> activities,
        List<String> missingFields
) {

    public ConversationImageFacts {
        activities = activities == null ? List.of() : List.copyOf(activities);
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }

    public boolean hasRecognizedFacts() {
        return notBlank(origin)
                || notBlank(destination)
                || notBlank(startDate)
                || notBlank(endDate)
                || days != null
                || notBlank(budget)
                || notBlank(hotelName)
                || notBlank(hotelArea)
                || !activities.isEmpty();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}

package com.travalagent.domain.model.valobj;

public record WeatherSnapshot(
        String city,
        String province,
        String reportTime,
        String description,
        String temperature,
        String windDirection,
        String windPower
) {
}

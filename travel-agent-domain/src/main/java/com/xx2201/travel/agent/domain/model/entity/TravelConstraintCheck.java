package com.xx2201.travel.agent.domain.model.entity;

public record TravelConstraintCheck(
        String code,
        ConstraintCheckStatus status,
        String message
) {
}


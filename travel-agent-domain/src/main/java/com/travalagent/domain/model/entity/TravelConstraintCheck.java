package com.travalagent.domain.model.entity;

public record TravelConstraintCheck(
        String code,
        ConstraintCheckStatus status,
        String message
) {
}


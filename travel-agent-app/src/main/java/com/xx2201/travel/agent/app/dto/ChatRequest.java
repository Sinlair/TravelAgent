package com.xx2201.travel.agent.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String conversationId,
        @NotBlank(message = "message cannot be blank") String message
) {
}

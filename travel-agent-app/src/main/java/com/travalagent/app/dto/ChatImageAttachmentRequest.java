package com.travalagent.app.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatImageAttachmentRequest(
        String name,
        @NotBlank(message = "mediaType cannot be blank") String mediaType,
        @NotBlank(message = "dataUrl cannot be blank") String dataUrl
) {
}

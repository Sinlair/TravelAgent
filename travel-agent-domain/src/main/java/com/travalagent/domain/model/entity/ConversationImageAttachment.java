package com.travalagent.domain.model.entity;

public record ConversationImageAttachment(
        String id,
        String name,
        String mediaType,
        int sizeBytes
) {
}

package com.travalagent.domain.model.valobj;

import java.time.Instant;
import java.util.Map;

public record LongTermMemoryItem(
        String id,
        String conversationId,
        String category,
        String content,
        Map<String, Object> metadata,
        Instant createdAt
) {
}

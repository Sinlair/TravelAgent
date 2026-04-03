package com.xx2201.travel.agent.domain.model.entity;

import com.xx2201.travel.agent.domain.model.valobj.ExecutionStage;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TimelineEvent(
        String id,
        String conversationId,
        ExecutionStage stage,
        String message,
        Map<String, Object> details,
        Instant createdAt
) {

    public static TimelineEvent of(
            String conversationId,
            ExecutionStage stage,
            String message,
            Map<String, Object> details
    ) {
        return new TimelineEvent(
                UUID.randomUUID().toString(),
                conversationId,
                stage,
                message,
                details == null ? Map.of() : Map.copyOf(details),
                Instant.now()
        );
    }
}

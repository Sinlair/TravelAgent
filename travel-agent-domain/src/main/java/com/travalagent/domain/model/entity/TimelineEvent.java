package com.travalagent.domain.model.entity;

import com.travalagent.domain.model.valobj.ExecutionStage;
import com.travalagent.domain.model.valobj.TimelineEventStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record TimelineEvent(
        String id,
        String conversationId,
        ExecutionStage stage,
        TimelineEventStatus status,
        String message,
        Map<String, Object> details,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt
) {

    public TimelineEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
        status = status == null ? TimelineEventStatus.COMPLETED : status;
        Instant baseline = createdAt == null ? Instant.now() : createdAt;
        startedAt = startedAt == null ? baseline : startedAt;
        endedAt = endedAt == null ? baseline : endedAt;
        createdAt = createdAt == null ? endedAt : createdAt;
    }

    public static TimelineEvent of(
            String conversationId,
            ExecutionStage stage,
            String message,
            Map<String, Object> details
    ) {
        return of(conversationId, stage, TimelineEventStatus.COMPLETED, message, details);
    }

    public static TimelineEvent of(
            String conversationId,
            ExecutionStage stage,
            TimelineEventStatus status,
            String message,
            Map<String, Object> details
    ) {
        Instant now = Instant.now();
        return new TimelineEvent(
                UUID.randomUUID().toString(),
                conversationId,
                stage,
                status,
                message,
                normalizedDetails(details, status, now),
                now,
                now,
                now
        );
    }

    private static Map<String, Object> normalizedDetails(
            Map<String, Object> details,
            TimelineEventStatus status,
            Instant now
    ) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (details != null) {
            normalized.putAll(details);
        }
        normalized.putIfAbsent("status", status.name());
        normalized.putIfAbsent("startedAt", now.toString());
        normalized.putIfAbsent("endedAt", now.toString());
        return Map.copyOf(normalized);
    }
}

package com.tino.backend.sync.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Framework-independent Android event envelope after adapter validation. */
public record SyncEvent(
        UUID eventId,
        String storeId,
        String deviceId,
        String aggregateId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String payloadJson) {
    public SyncEvent {
        Objects.requireNonNull(eventId, "eventId");
        requireText(storeId, "storeId");
        requireText(deviceId, "deviceId");
        requireText(aggregateId, "aggregateId");
        requireText(eventType, "eventType");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        requireText(payloadJson, "payloadJson");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

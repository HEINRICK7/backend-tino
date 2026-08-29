package com.tino.backend.sync.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Client-facing immutable change envelope returned by Sync Pull. */
public record SyncChange(
        UUID eventId,
        String storeId,
        String deviceId,
        String aggregateId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String payloadJson) {
    public SyncChange {
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

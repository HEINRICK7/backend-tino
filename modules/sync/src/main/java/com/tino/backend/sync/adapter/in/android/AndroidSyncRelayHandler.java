package com.tino.backend.sync.adapter.in.android;

import com.tino.backend.sync.application.port.in.SyncEventHandler;
import com.tino.backend.sync.domain.model.SyncEvent;
import com.tino.backend.sync.domain.model.SyncEventEffects;
import java.util.Objects;

/**
 * Relays an approved Android domain event through the tenant-scoped cloud log.
 *
 * <p>The Android device remains the projection owner for this first cloud
 * sync slice. The backend validates the envelope, claims it idempotently and
 * exposes the same canonical payload to the other installations. Unknown
 * event types still fail closed because each type is registered explicitly.
 */
final class AndroidSyncRelayHandler implements SyncEventHandler {
    private final String eventType;

    AndroidSyncRelayHandler(String eventType) {
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public SyncEventEffects handle(SyncEvent event) {
        if (!eventType.equals(event.eventType())) {
            throw new IllegalArgumentException("event type does not match relay handler");
        }
        return new SyncEventEffects(event.payloadJson(), event.payloadJson());
    }
}

package com.tino.backend.sync.domain.model;

/** Immutable effects returned by a registered handler for one accepted event. */
public record SyncEventEffects(String changePayloadJson, String outboxPayloadJson) {
    public SyncEventEffects {
        requireText(changePayloadJson, "changePayloadJson");
        requireText(outboxPayloadJson, "outboxPayloadJson");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

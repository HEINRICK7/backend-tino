package com.tino.backend.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Minimal trusted inbound evidence linking a provider event to one OTP challenge. */
public record OtpVerificationEvent(
        String providerEventId,
        UUID challengeId,
        String providerMessageId,
        PhoneNumber senderPhone,
        Instant occurredAt,
        Instant receivedAt) {
    public OtpVerificationEvent {
        requireText(providerEventId, "providerEventId");
        Objects.requireNonNull(challengeId, "challengeId");
        requireText(providerMessageId, "providerMessageId");
        Objects.requireNonNull(senderPhone, "senderPhone");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}

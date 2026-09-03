package com.tino.backend.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Trusted provider receipt for the outbound OTP message. */
public record OtpDeliveryEvent(
        String providerEventId,
        UUID challengeId,
        String providerMessageId,
        PhoneNumber recipientPhone,
        String eventType,
        Instant occurredAt,
        Instant receivedAt) {
    public OtpDeliveryEvent {
        requireText(providerEventId, "providerEventId");
        Objects.requireNonNull(challengeId, "challengeId");
        requireText(providerMessageId, "providerMessageId");
        Objects.requireNonNull(recipientPhone, "recipientPhone");
        if (!"AUTH_DELIVERED".equals(eventType) && !"AUTH_DELIVERY_FAILED".equals(eventType)) {
            throw new IllegalArgumentException("unsupported OTP delivery event");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}

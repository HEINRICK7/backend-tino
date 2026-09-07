package com.tino.backend.business.application.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Server-side binding between a verified OTP challenge and one business. */
public record PhoneChangeRequest(
        UUID challengeId,
        UUID userId,
        BusinessId businessId,
        String newPhoneE164,
        Status status,
        Instant createdAt,
        Instant completedAt) {

    public PhoneChangeRequest {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(businessId, "businessId");
        if (newPhoneE164 == null || newPhoneE164.isBlank()) {
            throw new IllegalArgumentException("newPhoneE164 is required");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if ((status == Status.COMPLETED) != (completedAt != null)) {
            throw new IllegalArgumentException("completedAt must match phone change status");
        }
    }

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED
    }
}

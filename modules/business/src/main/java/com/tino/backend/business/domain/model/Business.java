package com.tino.backend.business.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;

/** Authoritative tenant root, intentionally separate from an authenticated User. */
public record Business(
        BusinessId id,
        BusinessName tradeName,
        BusinessVertical vertical,
        BusinessStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Business {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tradeName, "tradeName");
        Objects.requireNonNull(vertical, "vertical");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Business active(
            BusinessId id,
            BusinessName tradeName,
            BusinessVertical vertical,
            Instant createdAt,
            Instant updatedAt) {
        return new Business(id, tradeName, vertical, BusinessStatus.ACTIVE, createdAt, updatedAt);
    }
}

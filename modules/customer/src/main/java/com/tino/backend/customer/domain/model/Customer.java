package com.tino.backend.customer.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Minimal tenant-owned customer; personal data is deliberately limited. */
public record Customer(
        UUID id,
        BusinessId businessId,
        String name,
        String nickname,
        String phone,
        CustomerStatus status,
        Instant createdAt,
        Instant updatedAt) {
    public Customer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        requireText(name, "name");
        optionalText(nickname, "nickname");
        optionalText(phone, "phone");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException(field + " must be non-blank and at most 200 characters");
        }
    }

    private static void optionalText(String value, String field) {
        if (value != null && (value.isBlank() || value.length() > (field.equals("phone") ? 32 : 100))) {
            throw new IllegalArgumentException(field + " is blank or too long");
        }
    }
}

package com.tino.backend.business.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;

public record BusinessPixConfiguration(
        BusinessId businessId,
        PixKey key,
        String copyPaste,
        String merchantName,
        String merchantCity,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt) {
    public BusinessPixConfiguration {
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(key, "key");
        if (copyPaste == null || copyPaste.isBlank()) throw new IllegalArgumentException("copyPaste is required");
        if (merchantName == null || merchantName.isBlank()) throw new IllegalArgumentException("merchantName is required");
        if (merchantCity == null || merchantCity.isBlank()) throw new IllegalArgumentException("merchantCity is required");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!enabled) throw new IllegalArgumentException("disabled Pix configuration must not be persisted");
    }
}

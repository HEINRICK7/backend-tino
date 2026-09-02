package com.tino.backend.businessunderstanding.domain.model;

import java.util.Objects;

/**
 * An explicit semantic signal supplied by a catalog or another trusted
 * classifier. It is evidence only; it never becomes a user confirmation.
 */
public record ItemPurposeHint(ItemPurpose purpose, String source, String reason) {
    public ItemPurposeHint {
        Objects.requireNonNull(purpose, "purpose");
        if (purpose == ItemPurpose.UNKNOWN) {
            throw new IllegalArgumentException("an item purpose hint cannot be UNKNOWN");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("hint source is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("hint reason is required");
        }
        source = source.trim();
        reason = reason.trim();
    }
}

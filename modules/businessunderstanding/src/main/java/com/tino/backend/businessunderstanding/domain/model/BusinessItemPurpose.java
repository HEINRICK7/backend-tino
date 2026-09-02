package com.tino.backend.businessunderstanding.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BusinessItemPurpose(
        UUID id,
        BusinessId businessId,
        UUID productId,
        String canonicalItemKey,
        UsageContext usageContext,
        ItemPurpose purpose,
        ItemPurposeSource source,
        BigDecimal confidence,
        long evidenceCount,
        String evidenceClassifiedBy,
        String evidenceReason,
        Instant evidenceAt,
        Instant firstObservedAt,
        Instant lastObservedAt,
        Instant createdAt,
        Instant updatedAt) {
    public BusinessItemPurpose {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        if ((productId == null) == (canonicalItemKey == null || canonicalItemKey.isBlank())) {
            throw new IllegalArgumentException("exactly one item identity is required");
        }
        if (canonicalItemKey != null) {
            canonicalItemKey = canonicalItemKey.trim();
        }
        Objects.requireNonNull(usageContext, "usageContext");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(confidence, "confidence");
        if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between zero and one");
        }
        if (evidenceCount < 0) {
            throw new IllegalArgumentException("evidence count cannot be negative");
        }
        if (evidenceClassifiedBy == null || evidenceClassifiedBy.isBlank()) {
            throw new IllegalArgumentException("evidence classified by is required");
        }
        if (evidenceReason == null || evidenceReason.isBlank()) {
            throw new IllegalArgumentException("evidence reason is required");
        }
        Objects.requireNonNull(evidenceAt, "evidenceAt");
        Objects.requireNonNull(firstObservedAt, "firstObservedAt");
        Objects.requireNonNull(lastObservedAt, "lastObservedAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static BusinessItemPurpose confirmed(
            BusinessId businessId, UUID productId, ItemPurpose purpose, Instant now) {
        return confirmed(businessId, productId, UsageContext.LEGACY, purpose, null,
                "Explicit user confirmation", now);
    }

    public static BusinessItemPurpose confirmed(
            BusinessId businessId, UUID productId, UsageContext usageContext,
            ItemPurpose purpose, UUID userId, String reason, Instant now) {
        var classifiedBy = userId == null ? "USER" : "USER:" + userId;
        return new BusinessItemPurpose(
                UUID.randomUUID(), businessId, productId, null, usageContext, purpose,
                ItemPurposeSource.USER_CONFIRMED, BigDecimal.ONE, 1, classifiedBy,
                reason == null || reason.isBlank() ? "Explicit user confirmation" : reason,
                now, now, now, now, now);
    }
}

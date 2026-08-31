package com.tino.backend.external.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ExternalBusinessConnection(
        UUID id, BusinessId businessId, String provider, ExternalConnectionStatus status,
        ExternalDataSourceType sourceType, Instant lastSuccessfulSyncAt, String syncCursor,
        Instant lastSyncStartedAt, Instant lastSyncFinishedAt, String lastSyncErrorCode,
        int lastSyncReceived, int lastSyncCreated, int lastSyncUpdated, int lastSyncDeactivated,
        int lastSyncRejected, Instant createdAt, Instant updatedAt) {
    public ExternalBusinessConnection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (sourceType == ExternalDataSourceType.EXTERNAL_API && (provider == null || provider.isBlank())) {
            throw new IllegalArgumentException("external provider is required");
        }
        if (lastSyncReceived < 0 || lastSyncCreated < 0 || lastSyncUpdated < 0 || lastSyncDeactivated < 0 || lastSyncRejected < 0) {
            throw new IllegalArgumentException("sync counters cannot be negative");
        }
    }
}

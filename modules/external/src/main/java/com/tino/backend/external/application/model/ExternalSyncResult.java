package com.tino.backend.external.application.model;

import com.tino.backend.external.domain.model.ExternalConnectionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExternalSyncResult(UUID connectionId, ExternalConnectionStatus status, Instant completedAt,
        int received, int created, int updated, int deactivated, int rejected, String errorCode) {}

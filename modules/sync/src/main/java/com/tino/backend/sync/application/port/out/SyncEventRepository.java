package com.tino.backend.sync.application.port.out;

import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.domain.model.SyncEvent;
import com.tino.backend.sync.domain.model.SyncEventEffects;
import java.time.Instant;
import java.util.UUID;

public interface SyncEventRepository {
    boolean claim(BusinessId businessId, SyncEvent event, Instant createdAt);

    void appendAccepted(
            BusinessId businessId,
            SyncEvent event,
            SyncEventEffects effects,
            UUID outboxId,
            Instant createdAt);

    void recordRejection(
            BusinessId businessId,
            UUID rejectionId,
            UUID eventId,
            String deviceId,
            String code,
            boolean retryable,
            String message,
            Instant createdAt);
}

package com.tino.backend.external.application.port.out;

import com.tino.backend.external.domain.model.ExternalBusinessConnection;
import com.tino.backend.external.domain.model.ExternalConnectionStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalBusinessConnectionRepository {
    ExternalBusinessConnection create(BusinessId businessId, String provider, Instant now);
    Optional<ExternalBusinessConnection> find(BusinessId businessId, UUID id);
    List<ExternalBusinessConnection> list(BusinessId businessId);
    ExternalBusinessConnection markSyncing(BusinessId businessId, UUID id, Instant now);
    void pageSucceeded(BusinessId businessId, UUID id, String cursor, int received, int created, int updated, int deactivated, int rejected, Instant now);
    ExternalBusinessConnection markSucceeded(BusinessId businessId, UUID id, String cursor, int received, int created, int updated, int deactivated, int rejected, Instant completedAt);
    ExternalBusinessConnection markFailed(BusinessId businessId, UUID id, ExternalConnectionStatus status, String errorCode, int received, int created, int updated, int deactivated, int rejected, Instant finishedAt);
}

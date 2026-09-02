package com.tino.backend.reconciliation.application.port.out;

import com.tino.backend.reconciliation.domain.model.ReconciliationClassification;
import com.tino.backend.reconciliation.domain.model.ReconciliationRunState;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRepository {
    Optional<RunRecord> findById(BusinessId businessId, UUID runId);
    Optional<RunRecord> findByIdempotency(BusinessId businessId, String key);
    void insertRun(RunRecord run);
    Optional<ItemRecord> findItem(BusinessId businessId, UUID runId, String provider, String providerEventId);
    void insertItem(ItemRecord item);
    void completeRun(BusinessId businessId, UUID runId, int matched, int discrepancies,
            ReconciliationRunState state, Instant completedAt);
    List<ItemRecord> findItems(BusinessId businessId, UUID runId);

    record RunRecord(UUID id, BusinessId businessId, String provider, String idempotencyKey,
            String fingerprint, ReconciliationRunState state, int totalCount, int matchedCount,
            int discrepancyCount, Instant createdAt, Instant completedAt) {}
    record ItemRecord(UUID id, BusinessId businessId, UUID runId, String provider,
            String providerEventId, String providerPaymentId, UUID paymentId, BigDecimal amount,
            String currency, String providerStatus, ReconciliationClassification classification,
            String payloadHash, Instant createdAt) {}
}

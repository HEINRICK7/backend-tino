package com.tino.backend.payment.application.port.out;

import com.tino.backend.payment.domain.model.PaymentEvidence;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentEvidenceRepository {
    Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key);

    boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID evidenceId, Instant createdAt);

    Optional<PaymentEvidence> find(BusinessId businessId, UUID evidenceId);

    Optional<PaymentEvidence> findByHash(BusinessId businessId, String evidenceHash);

    Optional<PaymentEvidence> findMatchedByIntent(BusinessId businessId, UUID paymentIntentId);

    default List<PaymentEvidence> findForReview(BusinessId businessId, int limit) {
        return List.of();
    }

    void insert(PaymentEvidence evidence);

    record IdempotencyRecord(String fingerprint, UUID evidenceId) {}
}

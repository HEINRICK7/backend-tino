package com.tino.backend.payment.application.port.out;

import com.tino.backend.payment.domain.model.Payment;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    boolean customerExists(BusinessId businessId, UUID customerId);
    Optional<Payment> find(BusinessId businessId, UUID paymentId);
    Optional<Payment> findByProviderPaymentId(BusinessId businessId, String provider, String providerPaymentId);
    Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key);
    boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID paymentId, Instant createdAt);
    void insert(Payment payment);
    void enqueue(OutboxCommand command);
    Optional<OutboxCommand> claimOutbox(BusinessId businessId, UUID paymentId, Instant now);
    void completeOutbox(BusinessId businessId, UUID outboxId, Instant completedAt);
    void failOutbox(BusinessId businessId, UUID outboxId, Instant availableAt, String error);
    Payment applyProviderEvent(BusinessId businessId, UUID paymentId, String provider,
            String providerEventId, String providerPaymentId, PaymentStatus status,
            String payloadHash, Instant createdAt);

    record IdempotencyRecord(String fingerprint, UUID paymentId) {}

    record OutboxCommand(UUID id, BusinessId businessId, UUID paymentId, String commandType,
            String state, int attemptCount, Instant availableAt, Instant createdAt) {}
}

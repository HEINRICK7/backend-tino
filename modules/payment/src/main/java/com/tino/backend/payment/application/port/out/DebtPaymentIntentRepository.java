package com.tino.backend.payment.application.port.out;

import com.tino.backend.payment.domain.model.DebtPaymentIntent;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface DebtPaymentIntentRepository {
    Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key);

    boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID paymentIntentId, Instant createdAt);

    void insert(DebtPaymentIntent intent);

    Optional<DebtPaymentIntent> find(BusinessId businessId, UUID paymentIntentId);

    default Optional<DebtPaymentIntent> findForUpdate(BusinessId businessId, UUID paymentIntentId) {
        return find(businessId, paymentIntentId);
    }

    default Optional<DebtPaymentIntent> findByTxid(BusinessId businessId, String pixTxid) {
        return Optional.empty();
    }

    /**
     * Resolves a notification without a txid only when one active intent is
     * unambiguously eligible for the amount and occurrence time.
     */
    default Optional<DebtPaymentIntent> findSingleOpenByAmount(BusinessId businessId,
            BigDecimal amount, Instant occurredAt) {
        return Optional.empty();
    }

    default Optional<DebtPaymentIntent> findOpenByCustomerAndAmount(BusinessId businessId,
            UUID customerId, BigDecimal amount, Instant now) {
        return Optional.empty();
    }

    default int markEvidenceFound(BusinessId businessId, UUID paymentIntentId, Instant updatedAt) {
        return 0;
    }

    default int markConfirmed(BusinessId businessId, UUID paymentIntentId, UUID creditEntryId, Instant updatedAt) {
        return 0;
    }

    default Optional<UUID> findConfirmedCreditEntryId(BusinessId businessId, UUID paymentIntentId) {
        return Optional.empty();
    }

    default Optional<ConfirmationIdempotencyRecord> findConfirmationIdempotency(BusinessId businessId, String key) {
        return Optional.empty();
    }

    default boolean claimConfirmationIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID paymentIntentId, UUID creditEntryId, Instant createdAt) {
        return false;
    }

    record IdempotencyRecord(String fingerprint, UUID paymentIntentId) {}

    record ConfirmationIdempotencyRecord(String fingerprint, UUID paymentIntentId, UUID creditEntryId) {}
}

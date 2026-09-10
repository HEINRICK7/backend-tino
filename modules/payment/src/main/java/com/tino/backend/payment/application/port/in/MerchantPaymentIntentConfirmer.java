package com.tino.backend.payment.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

public interface MerchantPaymentIntentConfirmer {
    Result execute(UUID authenticatedUserId, BusinessId businessId, UUID paymentIntentId,
            String idempotencyKey, String fingerprint);

    record Result(UUID paymentIntentId, UUID customerId, long amountMinor, UUID creditEntryId,
            String status, boolean replayed) {}

    final class ConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    final class EvidenceRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}

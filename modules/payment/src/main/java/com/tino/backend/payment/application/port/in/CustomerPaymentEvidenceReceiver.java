package com.tino.backend.payment.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface CustomerPaymentEvidenceReceiver {
    Result execute(UUID authenticatedUserId, BusinessId businessId, UUID paymentIntentId,
            BigDecimal amount, String currency, String pixTxid, String source, String sourcePackage,
            String evidenceHash, Instant occurredAt, String idempotencyKey, String fingerprint);

    record Result(UUID id, UUID paymentIntentId, String matchStatus, boolean replayed) {}

    final class ConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    final class IntentNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}

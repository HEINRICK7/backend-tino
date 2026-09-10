package com.tino.backend.payment.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public customer-channel boundary for creating a Pix checkout without exposing payment internals. */
public interface CustomerPaymentIntentCreator {
    Result execute(BusinessId businessId, UUID customerId, BigDecimal amount,
            String idempotencyKey, String fingerprint);

    record Result(UUID id, UUID customerId, long amountMinor, String currency, String pixTxid,
            String pixKey, String copyPaste, String status, Instant createdAt, Instant expiresAt,
            Instant updatedAt, boolean replayed) {}

    final class AmountExceedsBalanceException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    final class PixUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    final class ConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}

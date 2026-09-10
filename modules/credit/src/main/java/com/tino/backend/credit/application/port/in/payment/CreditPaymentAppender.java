package com.tino.backend.credit.application.port.in.payment;

import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.util.UUID;

/** Narrow credit boundary for payment workflows; the ledger model stays inside credit. */
public interface CreditPaymentAppender {
    Result execute(UUID authenticatedUserId, BusinessId businessId, UUID customerId,
            CreditDirection direction, BigDecimal amount, String reason,
            String idempotencyKey, String fingerprint);

    record Result(UUID entryId, boolean replayed) {}
}

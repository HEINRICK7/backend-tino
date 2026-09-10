package com.tino.backend.payment.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MerchantPaymentEvidenceReader {
    Result execute(UUID authenticatedUserId, BusinessId businessId, int limit);

    record Result(List<Item> items) {}

    record Item(UUID evidenceId, UUID paymentIntentId, UUID customerId, long amountMinor,
            String currency, String matchStatus, Instant occurredAt, String source) {}
}

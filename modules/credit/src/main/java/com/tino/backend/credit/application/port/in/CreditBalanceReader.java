package com.tino.backend.credit.application.port.in;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Read-only balance boundary for non-credit flows; it cannot mutate the ledger. */
public interface CreditBalanceReader {
    Optional<Balance> read(BusinessId businessId, UUID customerId);

    record Balance(BigDecimal amount, long version) {}
}

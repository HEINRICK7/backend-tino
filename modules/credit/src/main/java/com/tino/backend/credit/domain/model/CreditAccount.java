package com.tino.backend.credit.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditAccount(
        UUID id,
        BusinessId businessId,
        UUID customerId,
        String currency,
        BigDecimal balance,
        long version,
        Instant createdAt,
        Instant updatedAt) {}

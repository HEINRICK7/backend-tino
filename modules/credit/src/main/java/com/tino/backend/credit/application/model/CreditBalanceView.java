package com.tino.backend.credit.application.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.util.UUID;

public record CreditBalanceView(
        BusinessId businessId,
        UUID customerId,
        UUID accountId,
        String currency,
        BigDecimal balance,
        long version) {}

package com.tino.backend.payment.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record PaymentAmount(BigDecimal value) {
    public PaymentAmount {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0 || value.scale() > 2 || value.precision() - value.scale() > 17) {
            throw new IllegalArgumentException("payment amount must be positive BRL with at most two decimals");
        }
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }
}

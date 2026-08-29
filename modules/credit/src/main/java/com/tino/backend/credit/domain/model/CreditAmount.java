package com.tino.backend.credit.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact BRL amount used by the M9 ledger; floating-point values never enter the domain. */
public record CreditAmount(BigDecimal value) {
    private static final int MAX_INTEGER_DIGITS = 17;

    public CreditAmount {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0 || value.scale() > 2
                || value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException("credit amount must be positive with at most two decimals");
        }
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static CreditAmount of(BigDecimal value) {
        return new CreditAmount(value);
    }
}

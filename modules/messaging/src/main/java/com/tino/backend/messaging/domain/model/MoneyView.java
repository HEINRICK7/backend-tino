package com.tino.backend.messaging.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Exact presentation money; no floating-point conversion is allowed. */
public record MoneyView(String currency, BigDecimal amount) {
    public MoneyView {
        if (!"BRL".equals(currency) || amount == null || amount.scale() > 2
                || amount.signum() < 0 || amount.precision() - amount.scale() > 17) {
            throw new IllegalArgumentException("invalid money view");
        }
        amount = amount.stripTrailingZeros();
        if (amount.scale() < 0) {
            amount = amount.setScale(0);
        }
        Objects.requireNonNull(amount, "amount");
    }

    public static MoneyView brl(BigDecimal amount) {
        return new MoneyView("BRL", amount);
    }
}

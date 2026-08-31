package com.tino.backend.external.application.model;

import java.math.BigDecimal;

/** Canonical commercial option. It retains raw unit semantics and exact decimal money. */
public record ExternalPriceOption(
        String externalId, String label, BigDecimal quantity, String unit, String unitRaw,
        BigDecimal price, boolean defaultOption) {
    public ExternalPriceOption {
        if (externalId == null || externalId.isBlank()) throw new IllegalArgumentException("external option id is required");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("external option label is required");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("external option quantity must be positive");
        if (unit == null || unit.isBlank() || unitRaw == null || unitRaw.isBlank()) throw new IllegalArgumentException("external option unit is required");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("external option price must be non-negative");
    }
}

package com.tino.backend.catalog.application.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Generic commercial option received from an external read-only catalog. */
public record ExternalPriceOptionProjection(
        String externalId, String label, BigDecimal quantity, String unit, String unitRaw,
        BigDecimal price, boolean defaultOption) {
    public ExternalPriceOptionProjection {
        if (externalId == null || externalId.isBlank()) throw new IllegalArgumentException("external price option id is required");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("external price option quantity must be positive");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("external price option unit is required");
        if (unitRaw == null || unitRaw.isBlank()) throw new IllegalArgumentException("external price option raw unit is required");
        if (price == null || price.signum() < 0) throw new IllegalArgumentException("external price option price must be non-negative");
        Objects.requireNonNull(label, "label");
    }
}

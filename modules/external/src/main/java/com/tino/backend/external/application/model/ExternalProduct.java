package com.tino.backend.external.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral canonical product. No external DTO crosses this boundary. */
public record ExternalProduct(
        UUID providerConnectionId, String externalId, String name, boolean active, Instant updatedAt,
        BigDecimal defaultPrice, List<ExternalPriceOption> priceOptions, BigDecimal quantity,
        String unit, String unitRaw, String categoryContext, String subcategoryContext) {
    public ExternalProduct {
        Objects.requireNonNull(providerConnectionId, "providerConnectionId");
        if (externalId == null || externalId.isBlank()) throw new IllegalArgumentException("external product id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("external product name is required");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (defaultPrice != null && defaultPrice.signum() < 0) throw new IllegalArgumentException("external price must be non-negative");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("external quantity must be positive");
        if (unit == null || unit.isBlank() || unitRaw == null || unitRaw.isBlank()) throw new IllegalArgumentException("external unit is required");
        priceOptions = List.copyOf(Objects.requireNonNull(priceOptions, "priceOptions"));
    }
}

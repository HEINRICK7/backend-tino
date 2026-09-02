package com.tino.backend.catalog.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Provider-neutral projection accepted by Catalog. Provider DTOs stop before this type. */
public record ExternalProductProjection(
        UUID providerConnectionId, String externalId, String name, boolean active,
        Instant externalUpdatedAt, String unit, String unitRaw, BigDecimal defaultPrice,
        List<ExternalPriceOptionProjection> priceOptions, String categoryContext,
        String subcategoryContext, Instant syncedAt) {
    public ExternalProductProjection {
        Objects.requireNonNull(providerConnectionId, "providerConnectionId");
        if (externalId == null || externalId.isBlank()) throw new IllegalArgumentException("external product id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("external product name is required");
        Objects.requireNonNull(externalUpdatedAt, "externalUpdatedAt");
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("external product unit is required");
        if (unitRaw == null || unitRaw.isBlank()) throw new IllegalArgumentException("external product raw unit is required");
        if (defaultPrice != null && defaultPrice.signum() < 0) throw new IllegalArgumentException("external product price must be non-negative");
        priceOptions = List.copyOf(Objects.requireNonNull(priceOptions, "priceOptions"));
        Objects.requireNonNull(syncedAt, "syncedAt");
    }
}

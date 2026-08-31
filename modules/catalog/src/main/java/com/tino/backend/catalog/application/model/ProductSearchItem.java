package com.tino.backend.catalog.application.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** Minimal tenant-scoped product representation for mobile selection. */
public record ProductSearchItem(@JsonProperty("product_id") UUID productId, String name,
        @JsonProperty("base_unit") String baseUnit, String gtin,
        BigDecimal price) {}

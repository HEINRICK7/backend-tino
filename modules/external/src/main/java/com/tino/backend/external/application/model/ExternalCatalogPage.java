package com.tino.backend.external.application.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ExternalCatalogPage(List<ExternalProduct> products, String nextCursor, Instant watermark) {
    public ExternalCatalogPage {
        products = List.copyOf(Objects.requireNonNull(products, "products"));
        Objects.requireNonNull(watermark, "watermark");
        if (nextCursor != null && nextCursor.isBlank()) nextCursor = null;
    }
}

package com.tino.backend.receiving.application.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Canonical purchase input shared by fiscal adapters and Receiving. */
public record PurchaseDocument(
        Source source,
        DocumentType documentType,
        String accessKey,
        OffsetDateTime issuedAt,
        Issuer issuer,
        List<Item> items,
        BigDecimal total) {
    public PurchaseDocument {
        if (source == null) throw new IllegalArgumentException("source is required");
        if (documentType == null) throw new IllegalArgumentException("document type is required");
        if (accessKey == null) throw new IllegalArgumentException("access key is required");
        if (items == null) throw new IllegalArgumentException("items are required");
        items = List.copyOf(items);
    }

    public enum Source { NFCE }

    public enum DocumentType { NFCE }

    public record Issuer(String name, String taxId) {}

    public record Item(
            int lineNumber,
            String externalCode,
            String gtin,
            String rawDescription,
            BigDecimal quantity,
            String unit,
            BigDecimal unitPrice,
            BigDecimal totalPrice) {}
}

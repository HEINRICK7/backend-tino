package com.tino.backend.fiscal.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CanonicalNfeDocument(
        NfeAccessKey accessKey,
        String number,
        String series,
        Instant issuedAt,
        String natureOperation,
        Integer operationType,
        CanonicalNfeIssuer issuer,
        FiscalStatus fiscalStatus,
        List<CanonicalNfeItem> items,
        String parserVersion) {
    public CanonicalNfeDocument {
        Objects.requireNonNull(accessKey, "access key");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(fiscalStatus, "fiscal status");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.isEmpty()) throw new IllegalArgumentException("NF-e must contain at least one item");
        Objects.requireNonNull(parserVersion, "parser version");
    }
}

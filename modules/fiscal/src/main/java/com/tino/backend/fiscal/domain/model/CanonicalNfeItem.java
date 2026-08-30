package com.tino.backend.fiscal.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record CanonicalNfeItem(
        int lineNumber,
        String supplierProductCode,
        String gtin,
        String description,
        String ncm,
        String cest,
        String cfop,
        String commercialUnit,
        BigDecimal commercialQuantity,
        BigDecimal commercialUnitPrice,
        BigDecimal productTotal,
        String taxGtin,
        String taxUnit,
        BigDecimal taxQuantity,
        BigDecimal taxUnitPrice,
        BigDecimal discount,
        BigDecimal freight,
        BigDecimal insurance,
        BigDecimal otherValue,
        Boolean includedInTotal) {
    public CanonicalNfeItem {
        if (lineNumber < 1) throw new IllegalArgumentException("NF-e item line must be positive");
        Objects.requireNonNull(description, "NF-e item description");
        Objects.requireNonNull(commercialQuantity, "commercial quantity");
        Objects.requireNonNull(commercialUnitPrice, "commercial unit price");
        Objects.requireNonNull(productTotal, "product total");
    }
}

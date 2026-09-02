package com.tino.backend.receiving.application.model;

import java.math.BigDecimal;
import java.util.UUID;

/** Explicit, non-operational product resolution for one purchase-document item. */
public record PurchaseDocumentMatch(
        int lineNumber,
        Status status,
        UUID productId,
        String candidateName,
        String baseUnit,
        BigDecimal confidence,
        boolean requiresUserAction) {
    public enum Status { EXACT_MATCH, HIGH_CONFIDENCE_MATCH, REVIEW_REQUIRED, NEW_PRODUCT }
}

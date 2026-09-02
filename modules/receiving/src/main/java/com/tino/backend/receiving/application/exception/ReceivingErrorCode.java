package com.tino.backend.receiving.application.exception;

/** Stable machine-readable receiving error taxonomy owned by the HTTP boundary. */
public enum ReceivingErrorCode {
    INVALID_REQUEST,
    INVALID_ACCESS_KEY,
    NFE_NOT_FOUND,
    RETRIEVAL_UNAVAILABLE,
    OUTCOME_UNKNOWN,
    FISCAL_CANCELLED,
    FISCAL_DENIED,
    PRODUCT_REVIEW_REQUIRED,
    PACKAGING_CONVERSION_REQUIRED,
    STALE_PREVIEW,
    INVALID_PRODUCT_SELECTION,
    BUSINESS_ACCESS_DENIED,
    IDEMPOTENCY_CONFLICT
}

package com.tino.backend.receiving.application.model;

/** Stable public lifecycle of a human-reviewed goods receipt preview. */
public enum GoodsReceiptPreviewStatus {
    DRAFT,
    REVIEW_REQUIRED,
    READY,
    CONFIRMED,
    CANCELLED
}

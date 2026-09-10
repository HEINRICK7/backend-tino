package com.tino.backend.payment.domain.model;

public enum DebtPaymentIntentStatus {
    PENDING,
    EVIDENCE_FOUND,
    AWAITING_MERCHANT_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
    EXPIRED
}

package com.tino.backend.reconciliation.domain.model;

import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentStatus;
import java.util.Objects;

public record SettlementEntry(String providerEventId, String providerPaymentId, PaymentAmount amount,
        String currency, PaymentStatus status, String payloadSha256) {
    public SettlementEntry {
        if (providerEventId == null || providerEventId.isBlank() || providerPaymentId == null
                || providerPaymentId.isBlank() || !"BRL".equals(currency) || status == null
                || status == PaymentStatus.CREATED || payloadSha256 == null || payloadSha256.length() != 64) {
            throw new IllegalArgumentException("invalid normalized settlement entry");
        }
        Objects.requireNonNull(amount, "amount");
    }
}

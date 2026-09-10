package com.tino.backend.payment.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A customer-requested Pix checkout; it has no financial effect until confirmed. */
public record DebtPaymentIntent(
        UUID id,
        BusinessId businessId,
        UUID customerId,
        PaymentAmount amount,
        String pixTxid,
        DebtPaymentIntentStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt) {
    public DebtPaymentIntent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        if (pixTxid == null || !pixTxid.matches("TINO[A-Z0-9]{1,21}")) {
            throw new IllegalArgumentException("Pix txid must be opaque and TINO-prefixed");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("invalid payment intent timestamps");
        }
    }
}

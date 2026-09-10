package com.tino.backend.payment.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Normalized payment evidence. Raw bank notification text is deliberately not persisted. */
public record PaymentEvidence(
        UUID id,
        BusinessId businessId,
        UUID paymentIntentId,
        PaymentAmount amount,
        String currency,
        String pixTxid,
        String source,
        String sourcePackage,
        String evidenceHash,
        Instant occurredAt,
        Instant receivedAt,
        PaymentEvidenceMatchStatus matchStatus) {
    public PaymentEvidence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(amount, "amount");
        if (!"BRL".equals(currency)) throw new IllegalArgumentException("evidence currency must be BRL");
        if (source == null || !source.matches("[A-Z][A-Z0-9_]{2,39}")) {
            throw new IllegalArgumentException("evidence source is invalid");
        }
        if (sourcePackage == null || !sourcePackage.matches("[A-Za-z0-9_.]{1,200}")) {
            throw new IllegalArgumentException("evidence source package is invalid");
        }
        if (evidenceHash == null || !evidenceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("evidence hash must be lowercase SHA-256");
        }
        if (pixTxid != null && !pixTxid.matches("TINO[A-Z0-9]{1,21}")) {
            throw new IllegalArgumentException("evidence Pix txid is invalid");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(matchStatus, "matchStatus");
    }

    public BigDecimal amountValue() {
        return amount.value();
    }
}

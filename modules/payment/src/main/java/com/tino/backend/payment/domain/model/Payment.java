package com.tino.backend.payment.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Payment(
        UUID id,
        BusinessId businessId,
        UUID customerId,
        PaymentAmount amount,
        String currency,
        PaymentMethod method,
        String externalReference,
        String provider,
        String providerPaymentId,
        PaymentStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(amount, "amount");
        if (!"BRL".equals(currency) || method != PaymentMethod.PIX || provider == null || provider.isBlank()
                || status == null || version < 0) {
            throw new IllegalArgumentException("invalid payment");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (externalReference != null && externalReference.isBlank()) {
            throw new IllegalArgumentException("external reference cannot be blank");
        }
    }
}

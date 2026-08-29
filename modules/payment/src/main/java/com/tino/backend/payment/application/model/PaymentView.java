package com.tino.backend.payment.application.model;

import com.tino.backend.payment.domain.model.Payment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentView(UUID id, UUID businessId, UUID customerId, BigDecimal amount,
        String currency, String method, String externalReference, String provider,
        String providerPaymentId, String status, long version, Instant createdAt, Instant updatedAt) {
    public static PaymentView from(Payment payment) {
        return new PaymentView(payment.id(), payment.businessId().value(), payment.customerId(),
                payment.amount().value(), payment.currency(), payment.method().name(),
                payment.externalReference(), payment.provider(), payment.providerPaymentId(),
                payment.status().name(), payment.version(), payment.createdAt(), payment.updatedAt());
    }
}

package com.tino.backend.payment.application.usecase;

import com.tino.backend.payment.application.exception.PaymentConflictException;
import com.tino.backend.payment.application.exception.PaymentNotFoundException;
import com.tino.backend.payment.application.exception.PaymentTransitionException;
import com.tino.backend.payment.application.model.PaymentView;
import com.tino.backend.payment.application.port.out.PaymentProvider;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class IngestPaymentWebhook {
    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Clock clock;

    public IngestPaymentWebhook(PaymentRepository payments, PaymentProvider provider, Clock clock) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PaymentView execute(BusinessId businessId, UUID paymentId, String providerName,
            String providerEventId, String providerPaymentId, PaymentStatus status, String payloadHash) {
        if (!provider.name().equals(providerName) || providerEventId == null || providerEventId.isBlank()
                || providerPaymentId == null || providerPaymentId.isBlank() || status == null) {
            throw new PaymentConflictException();
        }
        var current = payments.find(businessId, paymentId).orElseThrow(PaymentNotFoundException::new);
        if (!provider.name().equals(current.provider())
                || (current.providerPaymentId() != null
                && !current.providerPaymentId().equals(providerPaymentId))) {
            throw new PaymentConflictException();
        }
        if (current.status() != status && !current.status().canTransitionTo(status)) {
            throw new PaymentTransitionException();
        }
        return PaymentView.from(payments.applyProviderEvent(businessId, paymentId, providerName,
                providerEventId, providerPaymentId, status, payloadHash, Instant.now(clock)));
    }
}

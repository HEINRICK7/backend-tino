package com.tino.backend.payment.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.exception.PaymentNotFoundException;
import com.tino.backend.payment.application.exception.PaymentProviderException;
import com.tino.backend.payment.application.model.PaymentCommandResult;
import com.tino.backend.payment.application.model.PaymentView;
import com.tino.backend.payment.application.port.out.PaymentProvider;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.domain.model.Payment;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class ProcessPayment {
    private final BusinessAuthorization authorization;
    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Clock clock;

    public ProcessPayment(BusinessAuthorization authorization, PaymentRepository payments,
            PaymentProvider provider, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PaymentCommandResult execute(UUID userId, BusinessId businessId, UUID paymentId) {
        var work = authorization.execute(userId, businessId, authorizedBusiness -> {
            var payment = payments.find(authorizedBusiness, paymentId)
                    .orElseThrow(PaymentNotFoundException::new);
            if (payment.status() != PaymentStatus.CREATED) {
                return new Work(payment, null);
            }
            return new Work(payment, payments.claimOutbox(authorizedBusiness, paymentId,
                    Instant.now(clock)).orElse(null));
        });
        if (work.command() == null) return new PaymentCommandResult(PaymentView.from(work.payment()), true);

        PaymentProvider.Authorization authorizationResult;
        try {
            authorizationResult = provider.authorize(work.payment());
        } catch (RuntimeException exception) {
            markFailed(userId, businessId, work.command(), exception);
            throw new PaymentProviderException(exception);
        }
        var result = authorization.execute(userId, businessId, authorizedBusiness -> {
            var updated = payments.applyProviderEvent(authorizedBusiness, paymentId, provider.name(),
                    authorizationResult.providerEventId(), authorizationResult.providerPaymentId(),
                    PaymentStatus.AUTHORIZED, digest(authorizationResult.providerEventId()), Instant.now(clock));
            payments.completeOutbox(authorizedBusiness, work.command().id(), Instant.now(clock));
            return new PaymentCommandResult(PaymentView.from(updated), false);
        });
        return result;
    }

    private void markFailed(UUID userId, BusinessId businessId,
            PaymentRepository.OutboxCommand command, RuntimeException cause) {
        authorization.execute(userId, businessId, authorizedBusiness -> {
            payments.failOutbox(authorizedBusiness, command.id(),
                    Instant.now(clock).plusSeconds(30), safeMessage(cause));
            return null;
        });
    }

    private static String safeMessage(RuntimeException exception) {
        var message = exception.getMessage();
        if (message == null || message.isBlank()) return "provider unavailable";
        return message.length() > 240 ? message.substring(0, 240) : message;
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record Work(Payment payment, PaymentRepository.OutboxCommand command) {}
}

package com.tino.backend.payment.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.exception.PaymentConflictException;
import com.tino.backend.payment.application.exception.PaymentCustomerNotFoundException;
import com.tino.backend.payment.application.model.PaymentCommandResult;
import com.tino.backend.payment.application.model.PaymentView;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.domain.model.Payment;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentMethod;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreatePayment {
    private static final String PROVIDER = "sandbox";
    private final BusinessAuthorization authorization;
    private final PaymentRepository payments;
    private final UuidGenerator ids;
    private final Clock clock;

    public CreatePayment(BusinessAuthorization authorization, PaymentRepository payments,
            UuidGenerator ids, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PaymentCommandResult execute(UUID userId, BusinessId businessId, UUID customerId,
            BigDecimal amount, String externalReference, String idempotencyKey, String fingerprint) {
        validateKey(idempotencyKey);
        validateFingerprint(fingerprint);
        var paymentAmount = new PaymentAmount(amount);
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            if (!payments.customerExists(authorizedBusiness, customerId)) {
                throw new PaymentCustomerNotFoundException();
            }
            var existing = payments.findIdempotency(authorizedBusiness, idempotencyKey);
            if (existing.isPresent()) {
                return replay(authorizedBusiness, existing.orElseThrow(), fingerprint);
            }
            var now = Instant.now(clock);
            var payment = new Payment(ids.next(), authorizedBusiness, customerId, paymentAmount,
                    "BRL", PaymentMethod.PIX, externalReference, PROVIDER, null,
                    PaymentStatus.CREATED, 0, now, now);
            if (!payments.claimIdempotency(authorizedBusiness, idempotencyKey, fingerprint,
                    payment.id(), now)) {
                var concurrent = payments.findIdempotency(authorizedBusiness, idempotencyKey)
                        .orElseThrow(PaymentConflictException::new);
                return replay(authorizedBusiness, concurrent, fingerprint);
            }
            payments.insert(payment);
            payments.enqueue(new PaymentRepository.OutboxCommand(ids.next(), authorizedBusiness,
                    payment.id(), "AUTHORIZE_PAYMENT", "PENDING", 0, now, now));
            return new PaymentCommandResult(PaymentView.from(payment), false);
        });
    }

    private PaymentCommandResult replay(BusinessId businessId,
            PaymentRepository.IdempotencyRecord record, String fingerprint) {
        if (!record.fingerprint().equals(fingerprint)) throw new PaymentConflictException();
        return new PaymentCommandResult(PaymentView.from(payments.find(businessId, record.paymentId())
                .orElseThrow(PaymentConflictException::new)), true);
    }

    private static void validateKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    private static void validateFingerprint(String value) {
        if (value == null || value.length() != 64 || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid request fingerprint");
        }
    }
}

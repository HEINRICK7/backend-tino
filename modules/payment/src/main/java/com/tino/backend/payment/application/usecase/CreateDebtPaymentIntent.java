package com.tino.backend.payment.application.usecase;

import com.tino.backend.business.application.port.in.BusinessPixReader;
import com.tino.backend.credit.application.port.in.CreditBalanceReader;
import com.tino.backend.payment.application.port.in.CustomerPaymentIntentCreator;
import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.domain.model.DebtPaymentIntent;
import com.tino.backend.payment.domain.model.DebtPaymentIntentStatus;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreateDebtPaymentIntent implements CustomerPaymentIntentCreator {
    private static final String OPERATION = "CREATE_DEBT_PAYMENT_INTENT";
    private static final Duration TTL = Duration.ofMinutes(30);
    private final CreditBalanceReader credits;
    private final DebtPaymentIntentRepository intents;
    private final BusinessPixReader pix;
    private final TenantContextExecutor tenants;
    private final UuidGenerator ids;
    private final Clock clock;

    public CreateDebtPaymentIntent(CreditBalanceReader credits, DebtPaymentIntentRepository intents,
            BusinessPixReader pix, TenantContextExecutor tenants, UuidGenerator ids, Clock clock) {
        this.credits = Objects.requireNonNull(credits, "credits");
        this.intents = Objects.requireNonNull(intents, "intents");
        this.pix = Objects.requireNonNull(pix, "pix");
        this.tenants = Objects.requireNonNull(tenants, "tenants");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CustomerPaymentIntentCreator.Result execute(BusinessId businessId, UUID customerId, BigDecimal amount,
            String idempotencyKey, String fingerprint) {
        validateKey(idempotencyKey);
        validateFingerprint(fingerprint);
        var paymentAmount = new PaymentAmount(amount);
        return tenants.execute(businessId, () -> {
            var existing = intents.findIdempotency(businessId, idempotencyKey);
            if (existing.isPresent()) return replay(businessId, existing.orElseThrow(), fingerprint);

            var balance = credits.read(businessId, customerId)
                    .map(CreditBalanceReader.Balance::amount)
                    .orElse(BigDecimal.ZERO.setScale(2));
            if (paymentAmount.value().compareTo(balance) > 0) {
                throw new CustomerPaymentIntentCreator.AmountExceedsBalanceException();
            }

            var now = Instant.now(clock);
            var openIntent = intents.findOpenByCustomerAndAmount(businessId, customerId,
                    paymentAmount.value(), now);
            if (openIntent.isPresent()) {
                var existingIntent = openIntent.orElseThrow();
                if (!intents.claimIdempotency(businessId, idempotencyKey, fingerprint,
                        existingIntent.id(), now)) {
                    var concurrent = intents.findIdempotency(businessId, idempotencyKey)
                            .orElseThrow(CustomerPaymentIntentCreator.ConflictException::new);
                    return replay(businessId, concurrent, fingerprint);
                }
                return replay(businessId,
                        new DebtPaymentIntentRepository.IdempotencyRecord(fingerprint, existingIntent.id()),
                        fingerprint);
            }
            var intentId = ids.next();
            var txid = txid(intentId);
            var pixView = pix.readForAmountAndTxid(businessId, paymentAmount.value(), txid)
                    .filter(BusinessPixReader.PixView::enabled)
                    .orElseThrow(CustomerPaymentIntentCreator.PixUnavailableException::new);
            var intent = new DebtPaymentIntent(intentId, businessId, customerId, paymentAmount, txid,
                    DebtPaymentIntentStatus.PENDING, now, now.plus(TTL), now);
            if (!intents.claimIdempotency(businessId, idempotencyKey, fingerprint, intentId, now)) {
                var concurrent = intents.findIdempotency(businessId, idempotencyKey)
                        .orElseThrow(CustomerPaymentIntentCreator.ConflictException::new);
                return replay(businessId, concurrent, fingerprint);
            }
            intents.insert(intent);
            return result(intent, pixView, false);
        });
    }

    private CustomerPaymentIntentCreator.Result replay(BusinessId businessId,
            DebtPaymentIntentRepository.IdempotencyRecord record, String fingerprint) {
        if (!record.fingerprint().equals(fingerprint)) throw new CustomerPaymentIntentCreator.ConflictException();
        var intent = intents.find(businessId, record.paymentIntentId())
                .orElseThrow(CustomerPaymentIntentCreator.ConflictException::new);
        var pixView = pix.readForAmountAndTxid(businessId, intent.amount().value(), intent.pixTxid())
                .filter(BusinessPixReader.PixView::enabled)
                .orElseThrow(CustomerPaymentIntentCreator.PixUnavailableException::new);
        return result(intent, pixView, true);
    }

    private static CustomerPaymentIntentCreator.Result result(
            DebtPaymentIntent intent, BusinessPixReader.PixView pix, boolean replayed) {
        return new CustomerPaymentIntentCreator.Result(intent.id(), intent.customerId(),
                intent.amount().value().movePointRight(2).longValueExact(), "BRL", intent.pixTxid(),
                pix.key(), pix.copyPaste(), intent.status().name(), intent.createdAt(), intent.expiresAt(),
                intent.updatedAt(), replayed);
    }

    private static String txid(UUID id) {
        return "TINO" + id.toString().replace("-", "").substring(0, 21).toUpperCase();
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

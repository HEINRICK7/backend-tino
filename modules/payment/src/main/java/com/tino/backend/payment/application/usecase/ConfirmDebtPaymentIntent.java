package com.tino.backend.payment.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.credit.application.port.in.payment.CreditPaymentAppender;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.payment.application.port.in.MerchantPaymentIntentConfirmer;
import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.application.port.out.PaymentEvidenceRepository;
import com.tino.backend.payment.domain.model.DebtPaymentIntentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

public final class ConfirmDebtPaymentIntent implements MerchantPaymentIntentConfirmer {
    private static final String LEDGER_OPERATION_PREFIX = "PIX_PAYMENT_INTENT:";
    private final BusinessAuthorization authorization;
    private final DebtPaymentIntentRepository intents;
    private final PaymentEvidenceRepository evidence;
    private final CreditPaymentAppender appendCreditEntry;
    private final Clock clock;

    public ConfirmDebtPaymentIntent(BusinessAuthorization authorization,
            DebtPaymentIntentRepository intents, PaymentEvidenceRepository evidence,
            CreditPaymentAppender appendCreditEntry, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.intents = Objects.requireNonNull(intents, "intents");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.appendCreditEntry = Objects.requireNonNull(appendCreditEntry, "appendCreditEntry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result execute(UUID authenticatedUserId, BusinessId businessId, UUID paymentIntentId,
            String idempotencyKey, String fingerprint) {
        validateKey(idempotencyKey);
        validateFingerprint(fingerprint);
        return authorization.execute(authenticatedUserId, businessId, authorizedBusiness -> {
            var intent = intents.findForUpdate(authorizedBusiness, paymentIntentId)
                    .orElseThrow(MerchantPaymentIntentConfirmer.ConflictException::new);
            var existingKey = intents.findConfirmationIdempotency(authorizedBusiness, idempotencyKey);
            if (existingKey.isPresent()) {
                var record = existingKey.orElseThrow();
                if (!record.fingerprint().equals(fingerprint)
                        || !record.paymentIntentId().equals(paymentIntentId)) {
                    throw new MerchantPaymentIntentConfirmer.ConflictException();
                }
                return result(intent.customerId(), intent.id(), intent.amount().value(),
                        record.creditEntryId(), intent.status().name(), true);
            }
            if (intent.status() == DebtPaymentIntentStatus.CONFIRMED) {
                var entryId = intents.findConfirmedCreditEntryId(authorizedBusiness, paymentIntentId)
                        .orElseThrow(MerchantPaymentIntentConfirmer.ConflictException::new);
                return result(intent.customerId(), intent.id(), intent.amount().value(), entryId,
                        intent.status().name(), true);
            }
            if (intent.status() != DebtPaymentIntentStatus.EVIDENCE_FOUND
                    && intent.status() != DebtPaymentIntentStatus.AWAITING_MERCHANT_CONFIRMATION) {
                throw new MerchantPaymentIntentConfirmer.EvidenceRequiredException();
            }
            var matched = evidence.findMatchedByIntent(authorizedBusiness, paymentIntentId)
                    .orElseThrow(MerchantPaymentIntentConfirmer.EvidenceRequiredException::new);
            if (matched.amount().value().compareTo(intent.amount().value()) != 0
                    || matched.occurredAt().isAfter(intent.expiresAt())) {
                throw new MerchantPaymentIntentConfirmer.EvidenceRequiredException();
            }

            var ledgerFingerprint = digest(LEDGER_OPERATION_PREFIX + paymentIntentId);
            var ledger = appendCreditEntry.execute(authenticatedUserId, authorizedBusiness,
                    intent.customerId(), CreditDirection.DEBIT, intent.amount().value(),
                    "PIX_PAYMENT_CONFIRMED", LEDGER_OPERATION_PREFIX + paymentIntentId,
                    ledgerFingerprint);
            var now = Instant.now(clock);
            if (intents.markConfirmed(authorizedBusiness, paymentIntentId, ledger.entryId(), now) != 1) {
                var currentEntry = intents.findConfirmedCreditEntryId(authorizedBusiness, paymentIntentId)
                        .orElseThrow(MerchantPaymentIntentConfirmer.ConflictException::new);
                if (!currentEntry.equals(ledger.entryId())) {
                    throw new MerchantPaymentIntentConfirmer.ConflictException();
                }
            }
            if (!intents.claimConfirmationIdempotency(authorizedBusiness, idempotencyKey, fingerprint,
                    paymentIntentId, ledger.entryId(), now)) {
                var concurrent = intents.findConfirmationIdempotency(authorizedBusiness, idempotencyKey)
                        .orElseThrow(MerchantPaymentIntentConfirmer.ConflictException::new);
                if (!concurrent.fingerprint().equals(fingerprint)
                        || !concurrent.paymentIntentId().equals(paymentIntentId)) {
                    throw new MerchantPaymentIntentConfirmer.ConflictException();
                }
            }
            return result(intent.customerId(), intent.id(), intent.amount().value(), ledger.entryId(),
                    DebtPaymentIntentStatus.CONFIRMED.name(), false);
        });
    }

    private static Result result(UUID customerId, UUID intentId, java.math.BigDecimal amount,
            UUID entryId, String status, boolean replayed) {
        return new Result(intentId, customerId, amount.movePointRight(2).longValueExact(), entryId,
                status, replayed);
    }

    private static void validateKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must be nonblank and at most 200 characters");
        }
    }

    private static void validateFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("request fingerprint must be lowercase SHA-256");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

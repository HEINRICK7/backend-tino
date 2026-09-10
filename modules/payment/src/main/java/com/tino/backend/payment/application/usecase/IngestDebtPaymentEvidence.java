package com.tino.backend.payment.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.port.in.CustomerPaymentEvidenceReceiver;
import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.application.port.out.PaymentEvidenceRepository;
import com.tino.backend.payment.domain.model.DebtPaymentIntent;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentEvidence;
import com.tino.backend.payment.domain.model.PaymentEvidenceMatchStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class IngestDebtPaymentEvidence implements CustomerPaymentEvidenceReceiver {
    private final BusinessAuthorization authorization;
    private final DebtPaymentIntentRepository intents;
    private final PaymentEvidenceRepository evidence;
    private final UuidGenerator ids;
    private final Clock clock;

    public IngestDebtPaymentEvidence(BusinessAuthorization authorization,
            DebtPaymentIntentRepository intents, PaymentEvidenceRepository evidence,
            UuidGenerator ids, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.intents = Objects.requireNonNull(intents, "intents");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result execute(UUID authenticatedUserId, BusinessId businessId, UUID paymentIntentId,
            BigDecimal amount, String currency, String pixTxid, String source, String sourcePackage,
            String evidenceHash, Instant occurredAt, String idempotencyKey, String fingerprint) {
        validateKey(idempotencyKey);
        validateFingerprint(fingerprint);
        var paymentAmount = new PaymentAmount(amount);
        return authorization.execute(authenticatedUserId, businessId, authorizedBusiness -> {
            var existingKey = evidence.findIdempotency(authorizedBusiness, idempotencyKey);
            if (existingKey.isPresent()) return replay(authorizedBusiness, existingKey.orElseThrow(), fingerprint);

            var duplicate = evidence.findByHash(authorizedBusiness, evidenceHash);
            if (duplicate.isPresent()) {
                var found = duplicate.orElseThrow();
                if (!samePayload(found, paymentIntentId, paymentAmount, currency, pixTxid, source, sourcePackage)) {
                    throw new CustomerPaymentEvidenceReceiver.ConflictException();
                }
                return result(found, true);
            }

            var now = Instant.now(clock);
            var target = resolveIntent(authorizedBusiness, paymentIntentId, pixTxid,
                    paymentAmount.value(), occurredAt);
            var targetId = target.map(DebtPaymentIntent::id).orElse(null);
            var matchStatus = target.map(intent -> matches(intent, paymentAmount, pixTxid, occurredAt))
                    .orElse(PaymentEvidenceMatchStatus.UNMATCHED);
            var candidate = new PaymentEvidence(ids.next(), authorizedBusiness, targetId, paymentAmount,
                    currency, pixTxid, source, sourcePackage, evidenceHash, occurredAt, now, matchStatus);
            if (!evidence.claimIdempotency(authorizedBusiness, idempotencyKey, fingerprint,
                    candidate.id(), now)) {
                var concurrent = evidence.findIdempotency(authorizedBusiness, idempotencyKey)
                        .orElseThrow(CustomerPaymentEvidenceReceiver.ConflictException::new);
                return replay(authorizedBusiness, concurrent, fingerprint);
            }
            evidence.insert(candidate);
            if (matchStatus == PaymentEvidenceMatchStatus.MATCHED && targetId != null) {
                intents.markEvidenceFound(authorizedBusiness, targetId, now);
            }
            return result(candidate, false);
        });
    }

    private Result replay(BusinessId businessId, PaymentEvidenceRepository.IdempotencyRecord record,
            String fingerprint) {
        var existing = evidence.find(businessId, record.evidenceId())
                .orElseThrow(CustomerPaymentEvidenceReceiver.ConflictException::new);
        if (!record.fingerprint().equals(fingerprint)) {
            throw new CustomerPaymentEvidenceReceiver.ConflictException();
        }
        return result(existing, true);
    }

    private Optional<DebtPaymentIntent> resolveIntent(BusinessId businessId, UUID paymentIntentId,
            String pixTxid, BigDecimal amount, Instant occurredAt) {
        if (paymentIntentId != null) {
            return Optional.of(intents.findForUpdate(businessId, paymentIntentId)
                    .orElseThrow(CustomerPaymentEvidenceReceiver.IntentNotFoundException::new));
        }
        return pixTxid == null
                ? intents.findSingleOpenByAmount(businessId, amount, occurredAt)
                : intents.findByTxid(businessId, pixTxid);
    }

    private static PaymentEvidenceMatchStatus matches(DebtPaymentIntent intent, PaymentAmount amount,
            String pixTxid, Instant occurredAt) {
        if (intent.status().name().equals("CANCELLED") || intent.status().name().equals("EXPIRED")) {
            return PaymentEvidenceMatchStatus.DIVERGENT;
        }
        if (intent.amount().value().compareTo(amount.value()) != 0) return PaymentEvidenceMatchStatus.DIVERGENT;
        if (pixTxid != null && !intent.pixTxid().equals(pixTxid)) return PaymentEvidenceMatchStatus.DIVERGENT;
        if (occurredAt.isAfter(intent.expiresAt())) return PaymentEvidenceMatchStatus.DIVERGENT;
        return PaymentEvidenceMatchStatus.MATCHED;
    }

    private static boolean samePayload(PaymentEvidence found, UUID paymentIntentId, PaymentAmount amount,
            String currency, String pixTxid, String source, String sourcePackage) {
        return Objects.equals(found.paymentIntentId(), paymentIntentId)
                && found.amount().value().compareTo(amount.value()) == 0
                && found.currency().equals(currency)
                && Objects.equals(found.pixTxid(), pixTxid)
                && found.source().equals(source)
                && found.sourcePackage().equals(sourcePackage);
    }

    private static Result result(PaymentEvidence value, boolean replayed) {
        return new Result(value.id(), value.paymentIntentId(), value.matchStatus().name(), replayed);
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
}

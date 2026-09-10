package com.tino.backend.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.credit.application.port.in.payment.CreditPaymentAppender;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.payment.application.port.in.CustomerPaymentEvidenceReceiver;
import com.tino.backend.payment.application.port.in.MerchantPaymentIntentConfirmer;
import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.application.port.out.PaymentEvidenceRepository;
import com.tino.backend.payment.domain.model.DebtPaymentIntent;
import com.tino.backend.payment.domain.model.DebtPaymentIntentStatus;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentEvidence;
import com.tino.backend.payment.domain.model.PaymentEvidenceMatchStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PaymentEvidenceWorkflowTest {
    private static final BusinessId BUSINESS_ID = new BusinessId(UUID.randomUUID());
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID INTENT_ID = UUID.randomUUID();
    private static final UUID EVIDENCE_ID = UUID.randomUUID();
    private static final UUID LEDGER_ENTRY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final String TXID = "TINOABC123";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final String EVIDENCE_HASH = "b".repeat(64);

    @Test
    void matchingEvidenceMovesIntentForwardAndReplaysSafely() {
        var intents = new MemoryIntents(intent(DebtPaymentIntentStatus.PENDING));
        var evidence = new MemoryEvidence();
        var useCase = new IngestDebtPaymentEvidence(authorizes(), intents, evidence,
                () -> EVIDENCE_ID, fixedClock());

        var result = useCase.execute(UUID.randomUUID(), BUSINESS_ID, INTENT_ID,
                new BigDecimal("25.00"), "BRL", TXID, "BANK_NOTIFICATION", "com.example.bank",
                EVIDENCE_HASH, NOW.minusSeconds(5), "evidence-1", FINGERPRINT);
        var replay = useCase.execute(UUID.randomUUID(), BUSINESS_ID, INTENT_ID,
                new BigDecimal("25.00"), "BRL", TXID, "BANK_NOTIFICATION", "com.example.bank",
                EVIDENCE_HASH, NOW.minusSeconds(5), "evidence-1", FINGERPRINT);

        assertThat(result.matchStatus()).isEqualTo("MATCHED");
        assertThat(result.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(intents.find(BUSINESS_ID, INTENT_ID).orElseThrow().status())
                .isEqualTo(DebtPaymentIntentStatus.EVIDENCE_FOUND);
        assertThat(evidence.values).hasSize(1);
    }

    @Test
    void matchesAnUnidentifiedBankReceiptOnlyWhenTheAmountHasOneOpenIntent() {
        var intents = new MemoryIntents(intent(DebtPaymentIntentStatus.PENDING));
        var evidence = new MemoryEvidence();
        var useCase = new IngestDebtPaymentEvidence(authorizes(), intents, evidence,
                () -> EVIDENCE_ID, fixedClock());

        var result = useCase.execute(UUID.randomUUID(), BUSINESS_ID, null,
                new BigDecimal("25.00"), "BRL", null, "BANK_NOTIFICATION", "com.example.bank",
                EVIDENCE_HASH, NOW.minusSeconds(5), "evidence-by-amount", FINGERPRINT);

        assertThat(result.matchStatus()).isEqualTo("MATCHED");
        assertThat(evidence.values.get(EVIDENCE_ID).paymentIntentId()).isEqualTo(INTENT_ID);
    }

    @Test
    void confirmationCannotCreateALedgerEntryWithoutMatchedEvidence() {
        var intents = new MemoryIntents(intent(DebtPaymentIntentStatus.PENDING));
        var appender = new RecordingCreditAppender();
        var useCase = new ConfirmDebtPaymentIntent(authorizes(), intents, new MemoryEvidence(),
                appender, fixedClock());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), BUSINESS_ID, INTENT_ID,
                "confirmation-1", FINGERPRINT))
                .isInstanceOf(MerchantPaymentIntentConfirmer.EvidenceRequiredException.class);
        assertThat(appender.calls).isZero();
    }

    @Test
    void confirmationAppendsOneDebitAndIsIdempotent() {
        var intents = new MemoryIntents(intent(DebtPaymentIntentStatus.EVIDENCE_FOUND));
        var evidence = new MemoryEvidence();
        evidence.values.put(EVIDENCE_ID, evidence());
        var appender = new RecordingCreditAppender();
        var useCase = new ConfirmDebtPaymentIntent(authorizes(), intents, evidence,
                appender, fixedClock());

        var result = useCase.execute(UUID.randomUUID(), BUSINESS_ID, INTENT_ID,
                "confirmation-1", FINGERPRINT);
        var replay = useCase.execute(UUID.randomUUID(), BUSINESS_ID, INTENT_ID,
                "confirmation-1", FINGERPRINT);

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.creditEntryId()).isEqualTo(LEDGER_ENTRY_ID);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.creditEntryId()).isEqualTo(LEDGER_ENTRY_ID);
        assertThat(appender.calls).isEqualTo(1);
        assertThat(intents.find(BUSINESS_ID, INTENT_ID).orElseThrow().status())
                .isEqualTo(DebtPaymentIntentStatus.CONFIRMED);
    }

    @Test
    void reviewQueueReturnsOnlyEvidenceForTheAuthorizedBusiness() {
        var intents = new MemoryIntents(intent(DebtPaymentIntentStatus.EVIDENCE_FOUND));
        var evidence = new MemoryEvidence();
        evidence.values.put(EVIDENCE_ID, evidence());

        var result = new ReadPaymentEvidenceQueue(authorizes(), evidence, intents)
                .execute(UUID.randomUUID(), BUSINESS_ID, 20);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.paymentIntentId()).isEqualTo(INTENT_ID);
            assertThat(item.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(item.amountMinor()).isEqualTo(2500L);
        });
    }

    private static DebtPaymentIntent intent(DebtPaymentIntentStatus status) {
        return new DebtPaymentIntent(INTENT_ID, BUSINESS_ID, CUSTOMER_ID,
                new PaymentAmount(new BigDecimal("25.00")), TXID, status,
                NOW.minusSeconds(60), NOW.plusSeconds(1_700), NOW.minusSeconds(60));
    }

    private static PaymentEvidence evidence() {
        return new PaymentEvidence(EVIDENCE_ID, BUSINESS_ID, INTENT_ID,
                new PaymentAmount(new BigDecimal("25.00")), "BRL", TXID,
                "BANK_NOTIFICATION", "com.example.bank", EVIDENCE_HASH,
                NOW.minusSeconds(5), NOW, PaymentEvidenceMatchStatus.MATCHED);
    }

    private static BusinessAuthorization authorizes() {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId requestedBusiness, Function<BusinessId, T> operation) {
                return operation.apply(requestedBusiness);
            }
        };
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static final class MemoryIntents implements DebtPaymentIntentRepository {
        private final Map<UUID, DebtPaymentIntent> values = new HashMap<>();
        private final Map<String, ConfirmationIdempotencyRecord> confirmations = new HashMap<>();
        private final Map<String, IdempotencyRecord> creations = new HashMap<>();

        private MemoryIntents(DebtPaymentIntent intent) {
            values.put(intent.id(), intent);
        }

        @Override
        public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
            return Optional.ofNullable(creations.get(key));
        }

        @Override
        public boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
                UUID paymentIntentId, Instant createdAt) {
            return creations.putIfAbsent(key, new IdempotencyRecord(fingerprint, paymentIntentId)) == null;
        }

        @Override
        public void insert(DebtPaymentIntent intent) {
            values.put(intent.id(), intent);
        }

        @Override
        public Optional<DebtPaymentIntent> find(BusinessId businessId, UUID paymentIntentId) {
            return Optional.ofNullable(values.get(paymentIntentId));
        }

        @Override
        public Optional<DebtPaymentIntent> findForUpdate(BusinessId businessId, UUID paymentIntentId) {
            return find(businessId, paymentIntentId);
        }

        @Override
        public Optional<DebtPaymentIntent> findSingleOpenByAmount(BusinessId businessId,
                BigDecimal amount, Instant occurredAt) {
            return values.values().stream()
                    .filter(value -> value.status() == DebtPaymentIntentStatus.PENDING)
                    .filter(value -> value.amount().value().compareTo(amount) == 0)
                    .filter(value -> !value.createdAt().isAfter(occurredAt))
                    .filter(value -> !value.expiresAt().isBefore(occurredAt))
                    .findFirst();
        }

        @Override
        public int markEvidenceFound(BusinessId businessId, UUID paymentIntentId, Instant updatedAt) {
            var current = values.get(paymentIntentId);
            if (current == null) return 0;
            values.put(paymentIntentId, new DebtPaymentIntent(current.id(), current.businessId(), current.customerId(),
                    current.amount(), current.pixTxid(), DebtPaymentIntentStatus.EVIDENCE_FOUND,
                    current.createdAt(), current.expiresAt(), updatedAt));
            return 1;
        }

        @Override
        public int markConfirmed(BusinessId businessId, UUID paymentIntentId, UUID creditEntryId,
                Instant updatedAt) {
            var current = values.get(paymentIntentId);
            if (current == null || current.status() == DebtPaymentIntentStatus.CONFIRMED) return 0;
            values.put(paymentIntentId, new DebtPaymentIntent(current.id(), current.businessId(), current.customerId(),
                    current.amount(), current.pixTxid(), DebtPaymentIntentStatus.CONFIRMED,
                    current.createdAt(), current.expiresAt(), updatedAt));
            confirmedEntry = creditEntryId;
            return 1;
        }

        private UUID confirmedEntry;

        @Override
        public Optional<UUID> findConfirmedCreditEntryId(BusinessId businessId, UUID paymentIntentId) {
            return Optional.ofNullable(confirmedEntry);
        }

        @Override
        public Optional<ConfirmationIdempotencyRecord> findConfirmationIdempotency(
                BusinessId businessId, String key) {
            return Optional.ofNullable(confirmations.get(key));
        }

        @Override
        public boolean claimConfirmationIdempotency(BusinessId businessId, String key, String fingerprint,
                UUID paymentIntentId, UUID creditEntryId, Instant createdAt) {
            return confirmations.putIfAbsent(key,
                    new ConfirmationIdempotencyRecord(fingerprint, paymentIntentId, creditEntryId)) == null;
        }
    }

    private static final class MemoryEvidence implements PaymentEvidenceRepository {
        private final Map<UUID, PaymentEvidence> values = new HashMap<>();
        private final Map<String, IdempotencyRecord> keys = new HashMap<>();

        @Override
        public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
            return Optional.ofNullable(keys.get(key));
        }

        @Override
        public boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
                UUID evidenceId, Instant createdAt) {
            return keys.putIfAbsent(key, new IdempotencyRecord(fingerprint, evidenceId)) == null;
        }

        @Override
        public Optional<PaymentEvidence> find(BusinessId businessId, UUID evidenceId) {
            return Optional.ofNullable(values.get(evidenceId));
        }

        @Override
        public Optional<PaymentEvidence> findByHash(BusinessId businessId, String evidenceHash) {
            return values.values().stream().filter(value -> value.evidenceHash().equals(evidenceHash)).findFirst();
        }

        @Override
        public Optional<PaymentEvidence> findMatchedByIntent(BusinessId businessId, UUID paymentIntentId) {
            return values.values().stream()
                    .filter(value -> paymentIntentId.equals(value.paymentIntentId()))
                    .filter(value -> value.matchStatus() == PaymentEvidenceMatchStatus.MATCHED)
                    .findFirst();
        }

        @Override
        public java.util.List<PaymentEvidence> findForReview(BusinessId businessId, int limit) {
            return values.values().stream()
                    .filter(value -> value.matchStatus() == PaymentEvidenceMatchStatus.MATCHED)
                    .limit(limit)
                    .toList();
        }

        @Override
        public void insert(PaymentEvidence evidence) {
            values.put(evidence.id(), evidence);
        }
    }

    private static final class RecordingCreditAppender implements CreditPaymentAppender {
        private int calls;

        @Override
        public Result execute(UUID authenticatedUserId, BusinessId businessId, UUID customerId,
                CreditDirection direction, BigDecimal amount, String reason, String idempotencyKey,
                String fingerprint) {
            calls++;
            assertThat(direction).isEqualTo(CreditDirection.DEBIT);
            assertThat(amount).isEqualByComparingTo("25.00");
            return new Result(LEDGER_ENTRY_ID, calls > 1);
        }
    }
}

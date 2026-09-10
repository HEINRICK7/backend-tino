package com.tino.backend.payment.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessPixReader;
import com.tino.backend.credit.application.port.in.CreditBalanceReader;
import com.tino.backend.payment.application.port.in.CustomerPaymentIntentCreator;
import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.domain.model.DebtPaymentIntent;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateDebtPaymentIntentTest {
    private static final BusinessId BUSINESS_ID = new BusinessId(UUID.randomUUID());
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void createsAnAmountBoundIntentWithoutTouchingTheLedger() {
        var repository = new MemoryIntents();
        var pix = new RecordingPix();
        var useCase = useCase(repository, pix, new Credit(693.50));

        var result = useCase.execute(BUSINESS_ID, CUSTOMER_ID, new BigDecimal("50.00"), "request-1", FINGERPRINT);

        assertThat(result.replayed()).isFalse();
        assertThat(result.amountMinor()).isEqualTo(5000);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.pixTxid()).startsWith("TINO");
        assertThat(result.pixTxid()).hasSize(25);
        assertThat(pix.lastTxid).isEqualTo(result.pixTxid());
        assertThat(repository.intents).hasSize(1);
    }

    @Test
    void replaysTheSameIdempotentIntentAndRejectsDifferentPayloads() {
        var repository = new MemoryIntents();
        var useCase = useCase(repository, new RecordingPix(), new Credit(100));

        var first = useCase.execute(BUSINESS_ID, CUSTOMER_ID, new BigDecimal("50.00"), "request-2", FINGERPRINT);
        var replay = useCase.execute(BUSINESS_ID, CUSTOMER_ID, new BigDecimal("50.00"), "request-2", FINGERPRINT);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(replay.amountMinor()).isEqualTo(first.amountMinor());
        assertThat(replay.pixTxid()).isEqualTo(first.pixTxid());
        assertThatThrownBy(() -> useCase.execute(BUSINESS_ID, CUSTOMER_ID,
                new BigDecimal("40.00"), "request-2", "b".repeat(64)))
                .isInstanceOf(RuntimeException.class);
        assertThat(repository.intents).hasSize(1);
    }

    @Test
    void reusesAnOpenIntentAcrossPwaReloadsInsteadOfCreatingAmbiguousCharges() {
        var repository = new MemoryIntents();
        var useCase = useCase(repository, new RecordingPix(), new Credit(100));

        var first = useCase.execute(BUSINESS_ID, CUSTOMER_ID, new BigDecimal("50.00"),
                "request-first", FINGERPRINT);
        var second = useCase.execute(BUSINESS_ID, CUSTOMER_ID, new BigDecimal("50.00"),
                "request-after-reload", FINGERPRINT);

        assertThat(second.replayed()).isTrue();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(repository.intents).hasSize(1);
    }

    @Test
    void refusesAnIntentAboveTheCurrentAuthoritativeBalance() {
        var useCase = useCase(new MemoryIntents(), new RecordingPix(), new Credit(49.99));

        assertThatThrownBy(() -> useCase.execute(BUSINESS_ID, CUSTOMER_ID,
                new BigDecimal("50.00"), "request-3", FINGERPRINT))
                .isInstanceOf(CustomerPaymentIntentCreator.AmountExceedsBalanceException.class);
    }

    private static CreateDebtPaymentIntent useCase(MemoryIntents intents, RecordingPix pix, Credit credits) {
        TenantContextExecutor tenant = new TenantContextExecutor() {
            @Override
            public <T> T execute(BusinessId businessId, java.util.function.Supplier<T> operation) {
                return operation.get();
            }
        };
        return new CreateDebtPaymentIntent(credits, intents, pix, tenant,
                () -> UUID.fromString("018f6c22-7b7e-7000-8000-000000000001"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class RecordingPix implements BusinessPixReader {
        private String lastTxid;

        @Override
        public Optional<PixView> read(BusinessId businessId) {
            return Optional.empty();
        }

        @Override
        public Optional<PixView> readForAmount(BusinessId businessId, BigDecimal amount) {
            return Optional.of(new PixView(true, "pix-key", "legacy"));
        }

        @Override
        public Optional<PixView> readForAmountAndTxid(BusinessId businessId, BigDecimal amount, String txid) {
            lastTxid = txid;
            return Optional.of(new PixView(true, "pix-key", "pix-" + txid));
        }
    }

    private static final class Credit implements CreditBalanceReader {
        private final BigDecimal balance;

        private Credit(double balance) {
            this.balance = BigDecimal.valueOf(balance).setScale(2);
        }

        @Override public Optional<Balance> read(BusinessId businessId, UUID customerId) {
            return Optional.of(new Balance(balance, 1));
        }
    }

    private static final class MemoryIntents implements DebtPaymentIntentRepository {
        private final Map<String, IdempotencyRecord> keys = new HashMap<>();
        private final Map<UUID, DebtPaymentIntent> intents = new HashMap<>();

        @Override public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
            return Optional.ofNullable(keys.get(key));
        }
        @Override public boolean claimIdempotency(BusinessId businessId, String key, String fingerprint,
                UUID paymentIntentId, Instant createdAt) {
            if (keys.containsKey(key)) return false;
            keys.put(key, new IdempotencyRecord(fingerprint, paymentIntentId));
            return true;
        }
        @Override public void insert(DebtPaymentIntent intent) { intents.put(intent.id(), intent); }
        @Override public Optional<DebtPaymentIntent> find(BusinessId businessId, UUID paymentIntentId) {
            return Optional.ofNullable(intents.get(paymentIntentId));
        }

        @Override public Optional<DebtPaymentIntent> findOpenByCustomerAndAmount(BusinessId businessId,
                UUID customerId, BigDecimal amount, Instant now) {
            return intents.values().stream()
                    .filter(intent -> intent.customerId().equals(customerId))
                    .filter(intent -> intent.amount().value().compareTo(amount) == 0)
                    .filter(intent -> intent.status() == com.tino.backend.payment.domain.model.DebtPaymentIntentStatus.PENDING)
                    .filter(intent -> intent.expiresAt().isAfter(now))
                    .findFirst();
        }
    }
}

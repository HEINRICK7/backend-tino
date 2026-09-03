package com.tino.backend.identity.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.identity.adapter.out.crypto.HmacOtpSecretHasher;
import com.tino.backend.identity.application.exception.OtpRateLimitedException;
import com.tino.backend.identity.application.exception.OtpVerificationException;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpDeliveryPort;
import com.tino.backend.identity.application.port.out.OtpGenerator;
import com.tino.backend.identity.application.port.out.OtpSecretHasher;
import com.tino.backend.identity.application.port.out.OtpVerificationEventRepository;
import com.tino.backend.identity.application.model.OtpIdentityProof;
import com.tino.backend.identity.domain.model.OtpChallenge;
import com.tino.backend.identity.domain.model.OtpVerificationEvent;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OtpUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-31T15:00:00Z");
    private static final String PHONE = "+5586995922924";

    @Test
    void requestDoesNotReturnCodeAndVerifyCreatesOneTimeProof() {
        var repository = new InMemoryChallenges();
        var delivery = new CapturingDelivery();
        var generator = new FixedGenerator();
        var hasher = new HmacOtpSecretHasher("test-only-secret");
        var request = request(repository, delivery, generator, hasher);

        var issued = request.execute(PHONE, "127.0.0.1");
        assertThat(issued.challengeId()).isNotNull();
        assertThat(issued.toString()).doesNotContain("482731");
        assertThat(delivery.lastCode).isEqualTo("482731");

        var verified = new VerifyOtp(
                repository, generator, hasher, Clock.fixed(NOW, ZoneOffset.UTC))
                .execute(issued.challengeId(), "482731");
        assertThat(verified.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(verified.verificationTicket()).isEqualTo("ticket-1");

        var proof = new ConsumeOtpVerificationTicket(
                repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC))
                .execute(verified.verificationTicket());
        assertThat(proof).isEqualTo(new OtpIdentityProof(
                issued.challengeId(), com.tino.backend.identity.domain.model.PhoneNumber.normalize(PHONE), 60));
        assertThatThrownBy(() -> new ConsumeOtpVerificationTicket(
                repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC), "tino-android")
                .execute(verified.verificationTicket(), "another-client"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("reason")
                .isEqualTo(OtpVerificationException.Reason.INVALID);
        assertThatThrownBy(() -> new ConsumeOtpVerificationTicket(
                repository, hasher, Clock.fixed(NOW, ZoneOffset.UTC))
                .execute(verified.verificationTicket()))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("reason")
                .isEqualTo(OtpVerificationException.Reason.ALREADY_USED);
    }

    @Test
    void wrongCodeIsLimitedAndEventuallyLocksChallenge() {
        var repository = new InMemoryChallenges();
        var request = request(repository, new CapturingDelivery(), new FixedGenerator(),
                new HmacOtpSecretHasher("test-only-secret"));
        var issued = request.execute(PHONE, "127.0.0.1");
        var verify = new VerifyOtp(
                repository, new FixedGenerator(), new HmacOtpSecretHasher("test-only-secret"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        for (var attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> verify.execute(issued.challengeId(), "000000"))
                    .isInstanceOf(OtpVerificationException.class)
                    .extracting("reason")
                    .isEqualTo(OtpVerificationException.Reason.INVALID);
        }
        assertThatThrownBy(() -> verify.execute(issued.challengeId(), "000000"))
                .isInstanceOf(OtpVerificationException.class)
                .extracting("reason")
                .isEqualTo(OtpVerificationException.Reason.LOCKED);
    }

    @Test
    void secondImmediateRequestIsBlockedByCooldown() {
        var repository = new InMemoryChallenges();
        var hasher = new HmacOtpSecretHasher("test-only-secret");
        var request = request(repository, new CapturingDelivery(), new FixedGenerator(), hasher);
        request.execute(PHONE, "127.0.0.1");

        assertThatThrownBy(() -> request.execute(PHONE, "127.0.0.1"))
                .isInstanceOf(OtpRateLimitedException.class);
    }

    @Test
    void whatsappConfirmationIsIdempotentAndIssuesTheNormalKeycloakTicket() {
        var repository = new InMemoryChallenges();
        var events = new InMemoryVerificationEvents();
        var generator = new FixedGenerator();
        var hasher = new HmacOtpSecretHasher("test-only-secret");
        var issued = request(repository, new CapturingDelivery(), generator, hasher)
                .execute(PHONE, "127.0.0.1");
        var confirm = new ConfirmOtpFromWhatsApp(repository, events, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(confirm.execute(issued.challengeId(), "AUTH_CONFIRMED", "event-1", "message-1",
                PHONE, NOW)).isEqualTo(com.tino.backend.identity.domain.model.OtpChallengeStatus.VERIFIED);
        assertThat(confirm.execute(issued.challengeId(), "AUTH_CONFIRMED", "event-1", "message-1",
                PHONE, NOW)).isEqualTo(com.tino.backend.identity.domain.model.OtpChallengeStatus.VERIFIED);

        var status = new GetOtpChallengeStatus(repository, events, Clock.fixed(NOW, ZoneOffset.UTC))
                .execute(issued.challengeId());
        assertThat(status.status()).isEqualTo("VERIFIED");
        assertThat(status.verificationAvailable()).isTrue();

        var verification = new IssueOtpVerificationTicket(repository, events, generator, hasher,
                Clock.fixed(NOW, ZoneOffset.UTC)).execute(issued.challengeId());
        assertThat(verification.verificationStatus()).isEqualTo("VERIFIED");
        assertThat(verification.verificationTicket()).isEqualTo("ticket-1");
        assertThat(events.values).hasSize(1);
    }

    @Test
    void whatsappConfirmationRejectsADifferentSender() {
        var repository = new InMemoryChallenges();
        var events = new InMemoryVerificationEvents();
        var hasher = new HmacOtpSecretHasher("test-only-secret");
        var issued = request(repository, new CapturingDelivery(), new FixedGenerator(), hasher)
                .execute(PHONE, "127.0.0.1");

        assertThatThrownBy(() -> new ConfirmOtpFromWhatsApp(repository, events, Clock.fixed(NOW, ZoneOffset.UTC))
                .execute(issued.challengeId(), "AUTH_CONFIRMED", "event-2", "message-2",
                        "+5586995999999", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WhatsApp sender does not match challenge");
        assertThat(repository.findByIdForUpdate(issued.challengeId()).orElseThrow().status())
                .isEqualTo(com.tino.backend.identity.domain.model.OtpChallengeStatus.PENDING);
    }

    private static RequestOtp request(
            InMemoryChallenges repository,
            CapturingDelivery delivery,
            OtpGenerator generator,
            OtpSecretHasher hasher) {
        return new RequestOtp(
                repository,
                delivery,
                generator,
                hasher,
                new FixedUuidGenerator(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FixedUuidGenerator implements UuidGenerator {
        @Override
        public UUID next() {
            return UUID.fromString("0198f6e4-7e00-7a6a-8b1d-4d9b7b8f1001");
        }
    }

    private static final class FixedGenerator implements OtpGenerator {
        @Override public String code() { return "482731"; }
        @Override public String verificationTicket() { return "ticket-1"; }
    }

    private static final class CapturingDelivery implements OtpDeliveryPort {
        private String lastCode;

        @Override
        public OtpDeliveryResult deliver(OtpDeliveryRequest request) {
            lastCode = request.code();
            return new OtpDeliveryResult(Status.ACCEPTED, Channel.WHATSAPP);
        }
    }

    private static final class InMemoryChallenges implements OtpChallengeRepository {
        private final Map<UUID, OtpChallenge> values = new HashMap<>();

        @Override public void lockPhone(String phoneHash) { }

        @Override
        public Optional<OtpChallenge> findLatestPendingByPhoneHash(String phoneHash) {
            return values.values().stream()
                    .filter(c -> c.phoneHash().equals(phoneHash)
                            && c.status() == com.tino.backend.identity.domain.model.OtpChallengeStatus.PENDING)
                    .max(Comparator.comparing(OtpChallenge::createdAt));
        }

        @Override
        public long countCreatedSinceByPhoneHash(String phoneHash, Instant since) {
            return values.values().stream().filter(c -> c.phoneHash().equals(phoneHash)
                    && !c.createdAt().isBefore(since)).count();
        }

        @Override
        public long countCreatedSinceByOriginHash(String originHash, Instant since) {
            return values.values().stream().filter(c -> originHash.equals(c.requestOriginHash())
                    && !c.createdAt().isBefore(since)).count();
        }

        @Override public void insert(OtpChallenge challenge) { values.put(challenge.id(), challenge); }

        @Override
        public Optional<OtpChallenge> findByIdForUpdate(UUID challengeId) {
            return Optional.ofNullable(values.get(challengeId));
        }

        @Override
        public Optional<OtpChallenge> findByTicketHashForUpdate(String ticketHash) {
            return values.values().stream()
                    .filter(c -> ticketHash.equals(c.verificationTicketHash()))
                    .findFirst();
        }

        @Override public void update(OtpChallenge challenge) { values.put(challenge.id(), challenge); }

        @Override
        public int deleteFinishedBefore(Instant before) {
            var ids = new ArrayList<>(values.keySet());
            ids.removeIf(id -> values.get(id).expiresAt().isAfter(before));
            ids.forEach(values::remove);
            return ids.size();
        }
    }

    private static final class InMemoryVerificationEvents implements OtpVerificationEventRepository {
        private final Map<String, OtpVerificationEvent> values = new HashMap<>();

        @Override
        public Optional<OtpVerificationEvent> findByProviderEventId(String providerEventId) {
            return Optional.ofNullable(values.get(providerEventId));
        }

        @Override
        public Optional<OtpVerificationEvent> findByChallengeId(UUID challengeId) {
            return values.values().stream().filter(event -> event.challengeId().equals(challengeId)).findFirst();
        }

        @Override
        public void insert(OtpVerificationEvent event) {
            values.put(event.providerEventId(), event);
        }
    }
}

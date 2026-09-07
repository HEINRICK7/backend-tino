package com.tino.backend.business.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.model.PhoneChangeRequest;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.out.PhoneChangeRepository;
import com.tino.backend.business.application.usecase.CompletePhoneChange;
import com.tino.backend.identity.adapter.out.crypto.HmacOtpSecretHasher;
import com.tino.backend.identity.application.port.in.PhoneChangeOtpGateway;
import com.tino.backend.identity.application.port.in.PhoneChangeOtpGateway.PhoneChangeChallenge;
import com.tino.backend.identity.application.port.in.PhoneIdentityManagement;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.usecase.ConsumeOtpVerificationTicket;
import com.tino.backend.identity.domain.model.OtpChallenge;
import com.tino.backend.identity.domain.model.OtpChallengeStatus;
import com.tino.backend.identity.domain.model.PhoneNumber;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PhoneChangeUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.fromString("018f6d7a-2f35-7a8c-8db8-7b3b3f7f0f01");
    private static final UUID BUSINESS_ID = UUID.fromString("018f6d7a-2f35-7a8c-8db8-7b3b3f7f0f02");

    @Test
    void completesOnlyWhenTicketAndServerRequestMatch() {
        var challengeId = UUID.fromString("018f6d7a-2f35-7a8c-8db8-7b3b3f7f0f03");
        var ticket = "ticket-phone-change-12345678901234567890";
        var phone = PhoneNumber.normalize("(86) 9 1234-5678");
        var otp = otpRepository(challengeId, phone, ticket);
        var requests = new InMemoryPhoneChangeRequests(
                pending(challengeId, USER_ID, phone.e164()));
        var replaced = new java.util.ArrayList<String>();
        var useCase = useCase(otp, requests, (userId, newPhone) -> replaced.add(userId + ":" + newPhone));

        var result = useCase.execute(USER_ID, new BusinessId(BUSINESS_ID), challengeId, ticket);

        assertThat(result.phone()).isEqualTo(phone.e164());
        assertThat(result.status()).isEqualTo("UPDATED");
        assertThat(replaced).containsExactly(USER_ID + ":" + phone.e164());
        assertThat(requests.findByChallengeIdForUpdate(challengeId).orElseThrow().status())
                .isEqualTo(PhoneChangeRequest.Status.COMPLETED);
        assertThat(otp.challenges.get(challengeId).status()).isEqualTo(OtpChallengeStatus.CONSUMED);
    }

    @Test
    void rejectsTicketFromAnotherChallengeWithoutConsumingIt() {
        var requestChallengeId = UUID.fromString("018f6d7a-2f35-7a8c-8db8-7b3b3f7f0f04");
        var ticketChallengeId = UUID.fromString("018f6d7a-2f35-7a8c-8db8-7b3b3f7f0f05");
        var ticket = "ticket-other-challenge-12345678901234567890";
        var phone = PhoneNumber.normalize("(86) 9 1234-5678");
        var otp = otpRepository(ticketChallengeId, phone, ticket);
        var requests = new InMemoryPhoneChangeRequests(
                pending(requestChallengeId, USER_ID, phone.e164()));
        var replaced = new java.util.ArrayList<String>();
        var useCase = useCase(otp, requests, (userId, newPhone) -> replaced.add(newPhone));

        assertThatThrownBy(() -> useCase.execute(
                USER_ID, new BusinessId(BUSINESS_ID), requestChallengeId, ticket))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(replaced).isEmpty();
        assertThat(otp.challenges.get(ticketChallengeId).status()).isEqualTo(OtpChallengeStatus.VERIFIED);
        assertThat(requests.findByChallengeIdForUpdate(requestChallengeId).orElseThrow().status())
                .isEqualTo(PhoneChangeRequest.Status.PENDING);
    }

    @Test
    void rejectsForeignUserBeforeConsumingTheTicket() {
        var challengeId = UUID.fromString("018f6d7a-2f35-7a8c-8db8-7b3b3f7f0f06");
        var foreignUser = UUID.fromString("018f6d7a-2f35-7a8c-8db8-7b3b3f7f0f07");
        var ticket = "ticket-foreign-user-12345678901234567890";
        var phone = PhoneNumber.normalize("(86) 9 1234-5678");
        var otp = otpRepository(challengeId, phone, ticket);
        var requests = new InMemoryPhoneChangeRequests(
                pending(challengeId, USER_ID, phone.e164()));
        var useCase = useCase(otp, requests, (userId, newPhone) -> { });

        assertThatThrownBy(() -> useCase.execute(
                foreignUser, new BusinessId(BUSINESS_ID), challengeId, ticket))
                .isInstanceOf(com.tino.backend.business.application.exception.BusinessAccessDeniedException.class);

        assertThat(otp.challenges.get(challengeId).status()).isEqualTo(OtpChallengeStatus.VERIFIED);
    }

    private static CompletePhoneChange useCase(
            InMemoryOtpChallenges otp,
            InMemoryPhoneChangeRequests requests,
            PhoneIdentityManagement identities) {
        return new CompletePhoneChange(
                authorize(), requests,
                phoneOtpGateway(otp),
                identities, CLOCK);
    }

    private static PhoneChangeOtpGateway phoneOtpGateway(InMemoryOtpChallenges otp) {
        var consume = new ConsumeOtpVerificationTicket(otp, otp.hasher, CLOCK);
        return new PhoneChangeOtpGateway() {
            @Override
            public PhoneChangeChallenge request(String phoneE164, String requestOrigin, UUID businessId) {
                throw new UnsupportedOperationException("request is not used by completion tests");
            }

            @Override
            public void consume(
                    UUID challengeId,
                    String verificationTicket,
                    String expectedPhoneE164,
                    Runnable beforeConsume) {
                consume.execute(verificationTicket, "tino-android", proof -> {
                    if (!proof.challengeId().equals(challengeId)
                            || !proof.phone().e164().equals(expectedPhoneE164)) {
                        throw new IllegalArgumentException("verification ticket does not match phone change");
                    }
                    beforeConsume.run();
                });
            }
        };
    }

    private static BusinessAuthorization authorize() {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId businessId, Function<BusinessId, T> operation) {
                return operation.apply(businessId);
            }
        };
    }

    private static PhoneChangeRequest pending(UUID challengeId, UUID userId, String phone) {
        return new PhoneChangeRequest(
                challengeId, userId, new BusinessId(BUSINESS_ID), phone,
                PhoneChangeRequest.Status.PENDING, NOW, null);
    }

    private static InMemoryOtpChallenges otpRepository(UUID challengeId, PhoneNumber phone, String ticket) {
        var hasher = new HmacOtpSecretHasher("test-phone-change-secret");
        var challenge = OtpChallenge.pending(
                challengeId, phone, hasher.hashPhone(phone.e164()), null,
                hasher.hashCode(challengeId.toString(), phone.e164(), "123456"),
                NOW.plusSeconds(300), NOW.minusSeconds(30), NOW.minusSeconds(60), 5, 3)
                .verified(hasher.hashTicket(ticket), NOW.minusSeconds(10), NOW.plusSeconds(60));
        var repository = new InMemoryOtpChallenges(hasher);
        repository.challenges.put(challengeId, challenge);
        return repository;
    }

    private static final class InMemoryPhoneChangeRequests implements PhoneChangeRepository {
        private final Map<UUID, PhoneChangeRequest> values = new HashMap<>();

        private InMemoryPhoneChangeRequests(PhoneChangeRequest... requests) {
            for (var request : requests) {
                values.put(request.challengeId(), request);
            }
        }

        @Override
        public Optional<PhoneChangeRequest> findByChallengeIdForUpdate(UUID challengeId) {
            return Optional.ofNullable(values.get(challengeId));
        }

        @Override
        public Optional<PhoneChangeRequest> findPendingByUserBusinessAndPhone(
                UUID userId, UUID businessId, String phoneE164) {
            return values.values().stream()
                    .filter(value -> value.status() == PhoneChangeRequest.Status.PENDING)
                    .filter(value -> value.userId().equals(userId))
                    .filter(value -> value.businessId().value().equals(businessId))
                    .filter(value -> value.newPhoneE164().equals(phoneE164))
                    .findFirst();
        }

        @Override
        public void insert(PhoneChangeRequest request) {
            values.put(request.challengeId(), request);
        }

        @Override
        public void markCompleted(UUID challengeId, Instant completedAt) {
            var current = values.get(challengeId);
            values.put(challengeId, new PhoneChangeRequest(
                    current.challengeId(), current.userId(), current.businessId(), current.newPhoneE164(),
                    PhoneChangeRequest.Status.COMPLETED, current.createdAt(), completedAt));
        }
    }

    private static final class InMemoryOtpChallenges implements OtpChallengeRepository {
        private final HmacOtpSecretHasher hasher;
        private final Map<UUID, OtpChallenge> challenges = new HashMap<>();

        private InMemoryOtpChallenges(HmacOtpSecretHasher hasher) {
            this.hasher = hasher;
        }

        @Override public void lockPhone(String phoneHash) { }
        @Override public Optional<OtpChallenge> findLatestPendingByPhoneHash(String phoneHash) { return Optional.empty(); }
        @Override public long countCreatedSinceByPhoneHash(String phoneHash, Instant since) { return 0; }
        @Override public long countCreatedSinceByOriginHash(String originHash, Instant since) { return 0; }
        @Override public void insert(OtpChallenge challenge) { challenges.put(challenge.id(), challenge); }
        @Override public Optional<OtpChallenge> findByIdForUpdate(UUID challengeId) { return Optional.ofNullable(challenges.get(challengeId)); }
        @Override public Optional<OtpChallenge> findByTicketHashForUpdate(String ticketHash) {
            return challenges.values().stream().filter(value -> ticketHash.equals(value.verificationTicketHash())).findFirst();
        }
        @Override public Optional<OtpChallenge> findByProviderMessageIdForUpdate(String providerMessageId) { return Optional.empty(); }
        @Override public void update(OtpChallenge challenge) { challenges.put(challenge.id(), challenge); }
        @Override public int deleteFinishedBefore(Instant before) { return 0; }
    }
}

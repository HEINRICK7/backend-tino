package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.exception.OtpDeliveryException;
import com.tino.backend.identity.application.exception.OtpRateLimitedException;
import com.tino.backend.identity.application.model.OtpChallengeIssued;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpDeliveryPort;
import com.tino.backend.identity.application.port.out.OtpGenerator;
import com.tino.backend.identity.application.port.out.OtpSecretHasher;
import com.tino.backend.identity.domain.model.OtpChallenge;
import com.tino.backend.identity.domain.model.OtpLifecycleStatus;
import com.tino.backend.identity.domain.model.PhoneNumber;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Creates or safely resends a short-lived OTP without disclosing the code. */
public class RequestOtp {
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESENDS = 3;
    private static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final Duration PHONE_WINDOW = Duration.ofHours(1);
    private static final Duration ORIGIN_WINDOW = Duration.ofMinutes(10);
    private static final long MAX_PHONE_REQUESTS = 10;
    private static final long MAX_ORIGIN_REQUESTS = 40;

    private final OtpChallengeRepository challenges;
    private final OtpDeliveryPort delivery;
    private final OtpGenerator generator;
    private final OtpSecretHasher hasher;
    private final UuidGenerator ids;
    private final Clock clock;

    public RequestOtp(
            OtpChallengeRepository challenges,
            OtpDeliveryPort delivery,
            OtpGenerator generator,
            OtpSecretHasher hasher,
            UuidGenerator ids,
            Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtpChallengeIssued execute(String phoneInput, String requestOrigin) {
        var phone = PhoneNumber.normalize(phoneInput);
        var now = Instant.now(clock);
        var phoneHash = hasher.hashPhone(phone.e164());
        var originHash = requestOrigin == null || requestOrigin.isBlank()
                ? null
                : hasher.hashOrigin(requestOrigin);

        challenges.lockPhone(phoneHash);
        var current = challenges.findLatestPendingByPhoneHash(phoneHash);
        if (current.isPresent() && !current.orElseThrow().isExpired(now)) {
            var challenge = current.orElseThrow();
            if (challenge.resendAvailableAt().isAfter(now)) {
                throw new OtpRateLimitedException(secondsUntil(now, challenge.resendAvailableAt()));
            }
            if (challenge.resendCount() >= challenge.maxResends()) {
                throw new OtpRateLimitedException(secondsUntil(now, challenge.expiresAt()));
            }
            return deliverResend(challenge, now);
        }

        if (challenges.countCreatedSinceByPhoneHash(phoneHash, now.minus(PHONE_WINDOW)) >= MAX_PHONE_REQUESTS
                || (originHash != null
                        && challenges.countCreatedSinceByOriginHash(originHash, now.minus(ORIGIN_WINDOW))
                                >= MAX_ORIGIN_REQUESTS)) {
            throw new OtpRateLimitedException(PHONE_WINDOW.toSeconds());
        }

        var id = ids.next();
        var code = generator.code();
        var challenge = OtpChallenge.pending(
                id,
                phone,
                phoneHash,
                originHash,
                hasher.hashCode(id.toString(), phone.e164(), code),
                now.plus(CHALLENGE_LIFETIME),
                now.plus(RESEND_COOLDOWN),
                now,
                MAX_ATTEMPTS,
                MAX_RESENDS);
        challenges.insert(challenge);
        return deliver(challenge, code, now);
    }

    private OtpChallengeIssued deliverResend(OtpChallenge previous, Instant now) {
        var code = generator.code();
        var resent = previous.resent(
                hasher.hashCode(previous.id().toString(), previous.phone().e164(), code),
                now.plus(CHALLENGE_LIFETIME),
                now.plus(RESEND_COOLDOWN));
        challenges.update(resent);
        return deliver(resent, code, now);
    }

    private OtpChallengeIssued deliver(OtpChallenge challenge, String code, Instant now) {
        var result = delivery.deliver(new OtpDeliveryPort.OtpDeliveryRequest(
                challenge.phone(), "AUTH_OTP", code, 5, challenge.id()));
        if (result.status() != OtpDeliveryPort.Status.ACCEPTED
                || result.providerMessageId() == null) {
            challenges.update(challenge.deliveryFailed());
            throw new OtpDeliveryException(result.status() == OtpDeliveryPort.Status.RETRYABLE_FAILURE
                    || result.providerMessageId() == null);
        }
        var persisted = result.providerMessageId() == null
                ? challenge
                : challenge.withProviderMessageId(result.providerMessageId());
        if (result.providerMessageId() != null) {
            challenges.update(persisted);
        }
        return new OtpChallengeIssued(
                challenge.id(),
                secondsUntil(now, challenge.expiresAt()),
                secondsUntil(now, challenge.resendAvailableAt()),
                result.channel(),
                OtpLifecycleStatus.OTP_SENT);
    }

    private static long secondsUntil(Instant now, Instant future) {
        return Math.max(1, Duration.between(now, future).toSeconds());
    }
}

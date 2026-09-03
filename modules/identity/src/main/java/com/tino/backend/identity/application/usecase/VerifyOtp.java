package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.exception.OtpVerificationException;
import com.tino.backend.identity.application.model.OtpVerificationResult;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpGenerator;
import com.tino.backend.identity.application.port.out.OtpSecretHasher;
import com.tino.backend.identity.domain.model.OtpLifecycleStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Atomically validates and consumes an OTP challenge into a one-time proof. */
public class VerifyOtp {
    private static final Duration TICKET_LIFETIME = Duration.ofMinutes(1);

    private final OtpChallengeRepository challenges;
    private final OtpGenerator generator;
    private final OtpSecretHasher hasher;
    private final Clock clock;

    public VerifyOtp(
            OtpChallengeRepository challenges,
            OtpGenerator generator,
            OtpSecretHasher hasher,
            Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtpVerificationResult execute(UUID challengeId, String code) {
        if (challengeId == null || code == null || !code.matches("[0-9]{6}")) {
            throw new OtpVerificationException(OtpVerificationException.Reason.INVALID);
        }
        var challenge = challenges.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new OtpVerificationException(OtpVerificationException.Reason.INVALID));
        var now = Instant.now(clock);
        switch (challenge.status()) {
            case CONSUMED, VERIFIED -> throw new OtpVerificationException(
                    OtpVerificationException.Reason.ALREADY_USED);
            case LOCKED -> throw new OtpVerificationException(OtpVerificationException.Reason.LOCKED);
            case EXPIRED, DELIVERY_FAILED -> throw new OtpVerificationException(
                    OtpVerificationException.Reason.EXPIRED);
            case PENDING -> { }
        }
        if (challenge.isExpired(now)) {
            challenges.update(challenge.expired());
            throw new OtpVerificationException(OtpVerificationException.Reason.EXPIRED);
        }
        var expected = hasher.hashCode(challenge.id().toString(), challenge.phone().e164(), code);
        if (!constantTimeEquals(expected, challenge.codeVerifier())) {
            var attempted = challenge.invalidAttempt(now);
            challenges.update(attempted);
            var reason = attempted.status().name().equals("LOCKED")
                    ? OtpVerificationException.Reason.LOCKED
                    : OtpVerificationException.Reason.INVALID;
            throw new OtpVerificationException(reason);
        }
        var ticket = generator.verificationTicket();
        var ticketExpiry = now.plus(TICKET_LIFETIME);
        challenges.update(challenge.verified(hasher.hashTicket(ticket), now, ticketExpiry));
        return new OtpVerificationResult(
                challenge.id(), OtpLifecycleStatus.OTP_VERIFIED.name(), ticket, TICKET_LIFETIME.toSeconds());
    }

    private static boolean constantTimeEquals(String left, String right) {
        var a = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var b = right.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}

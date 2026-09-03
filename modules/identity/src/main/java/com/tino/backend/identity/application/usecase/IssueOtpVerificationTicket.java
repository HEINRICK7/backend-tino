package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.model.OtpVerificationResult;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpGenerator;
import com.tino.backend.identity.application.port.out.OtpSecretHasher;
import com.tino.backend.identity.application.port.out.OtpVerificationEventRepository;
import com.tino.backend.identity.domain.model.OtpChallengeStatus;
import com.tino.backend.identity.domain.model.OtpLifecycleStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Claims a ticket only for a challenge confirmed by inbound WhatsApp evidence. */
public final class IssueOtpVerificationTicket {
    private static final Duration TICKET_LIFETIME = Duration.ofMinutes(1);
    private final OtpChallengeRepository challenges;
    private final OtpVerificationEventRepository events;
    private final OtpGenerator generator;
    private final OtpSecretHasher hasher;
    private final Clock clock;

    public IssueOtpVerificationTicket(OtpChallengeRepository challenges,
            OtpVerificationEventRepository events, OtpGenerator generator,
            OtpSecretHasher hasher, Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.events = Objects.requireNonNull(events, "events");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtpVerificationResult execute(UUID challengeId) {
        var challenge = challenges.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("unknown OTP challenge"));
        if (challenge.status() == OtpChallengeStatus.CONSUMED) {
            throw new IllegalArgumentException("OTP verification ticket already used");
        }
        if (challenge.status() != OtpChallengeStatus.VERIFIED
                || events.findByChallengeId(challengeId).isEmpty()) {
            throw new IllegalArgumentException("OTP challenge is not WhatsApp verified");
        }
        var now = Instant.now(clock);
        if (challenge.isExpired(now)) {
            challenges.update(challenge.expired());
            throw new IllegalArgumentException("OTP challenge expired");
        }
        var ticket = generator.verificationTicket();
        var expiry = now.plus(TICKET_LIFETIME);
        challenges.update(challenge.withVerificationTicket(hasher.hashTicket(ticket), expiry));
        return new OtpVerificationResult(
                challengeId, OtpLifecycleStatus.OTP_VERIFIED.name(), ticket, TICKET_LIFETIME.toSeconds());
    }
}

package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.exception.OtpVerificationException;
import com.tino.backend.identity.application.model.OtpIdentityProof;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpSecretHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Consumes the short-lived proof reserved for the identity-provider bridge. */
public class ConsumeOtpVerificationTicket {
    private final OtpChallengeRepository challenges;
    private final OtpSecretHasher hasher;
    private final Clock clock;
    private final String allowedClientId;

    public ConsumeOtpVerificationTicket(
            OtpChallengeRepository challenges, OtpSecretHasher hasher, Clock clock) {
        this(challenges, hasher, clock, "tino-android");
    }

    public ConsumeOtpVerificationTicket(
            OtpChallengeRepository challenges, OtpSecretHasher hasher, Clock clock,
            String allowedClientId) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (allowedClientId == null || allowedClientId.isBlank()) {
            throw new IllegalArgumentException("allowedClientId is required");
        }
        this.allowedClientId = allowedClientId;
    }

    public OtpIdentityProof execute(String ticket) {
        return execute(ticket, allowedClientId);
    }

    public OtpIdentityProof execute(String ticket, String clientId) {
        if (ticket == null || ticket.isBlank()) {
            throw new OtpVerificationException(OtpVerificationException.Reason.INVALID);
        }
        if (!allowedClientId.equals(clientId)) {
            throw new OtpVerificationException(OtpVerificationException.Reason.INVALID);
        }
        var challenge = challenges.findByTicketHashForUpdate(hasher.hashTicket(ticket))
                .orElseThrow(() -> new OtpVerificationException(OtpVerificationException.Reason.INVALID));
        var now = Instant.now(clock);
        if (challenge.status() != com.tino.backend.identity.domain.model.OtpChallengeStatus.VERIFIED
                || challenge.verificationTicketExpiresAt() == null
                || !challenge.verificationTicketExpiresAt().isAfter(now)) {
            throw new OtpVerificationException(
                    challenge.status() == com.tino.backend.identity.domain.model.OtpChallengeStatus.CONSUMED
                            ? OtpVerificationException.Reason.ALREADY_USED
                            : OtpVerificationException.Reason.EXPIRED);
        }
        challenges.update(challenge.consumed(now));
        return new OtpIdentityProof(
                challenge.id(),
                challenge.phone(),
                Math.max(1, Duration.between(now, challenge.verificationTicketExpiresAt()).toSeconds()));
    }
}

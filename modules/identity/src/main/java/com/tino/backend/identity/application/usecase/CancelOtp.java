package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.model.OtpChallengeStatusView;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.domain.model.OtpChallengeStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Cancels an outstanding challenge without touching an already verified proof. */
public final class CancelOtp {
    private final OtpChallengeRepository challenges;
    private final GetOtpChallengeStatus getStatus;
    private final Clock clock;

    public CancelOtp(OtpChallengeRepository challenges, GetOtpChallengeStatus getStatus, Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.getStatus = Objects.requireNonNull(getStatus, "getStatus");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtpChallengeStatusView execute(UUID challengeId) {
        if (challengeId == null) {
            throw new IllegalArgumentException("challengeId is required");
        }
        var challenge = challenges.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("unknown OTP challenge"));
        if (challenge.status() == OtpChallengeStatus.PENDING
                || challenge.status() == OtpChallengeStatus.DELIVERED) {
            var now = Instant.now(clock);
            challenges.update(challenge.isExpired(now) ? challenge.expired() : challenge.cancelled());
        }
        return getStatus.execute(challengeId);
    }
}

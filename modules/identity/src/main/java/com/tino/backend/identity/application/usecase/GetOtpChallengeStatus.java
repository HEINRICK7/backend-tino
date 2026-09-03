package com.tino.backend.identity.application.usecase;

import com.tino.backend.identity.application.model.OtpChallengeStatusView;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpVerificationEventRepository;
import com.tino.backend.identity.domain.model.OtpChallenge;
import com.tino.backend.identity.application.exception.OtpVerificationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class GetOtpChallengeStatus {
    private final OtpChallengeRepository challenges;
    private final OtpVerificationEventRepository events;
    private final Clock clock;

    public GetOtpChallengeStatus(OtpChallengeRepository challenges,
            OtpVerificationEventRepository events, Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public OtpChallengeStatusView execute(UUID challengeId) {
        if (challengeId == null) throw new OtpVerificationException(OtpVerificationException.Reason.INVALID);
        var challenge = challenges.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new OtpVerificationException(OtpVerificationException.Reason.INVALID));
        var now = Instant.now(clock);
        if (challenge.status() == com.tino.backend.identity.domain.model.OtpChallengeStatus.PENDING
                && challenge.isExpired(now)) {
            challenge = challenge.expired();
            challenges.update(challenge);
        }
        var available = challenge.status()
                == com.tino.backend.identity.domain.model.OtpChallengeStatus.VERIFIED
                && events.findByChallengeId(challengeId).isPresent();
        return new OtpChallengeStatusView(
                challenge.id(), challenge.status().canonical().name(),
                Math.max(0, Duration.between(now, challenge.expiresAt()).toSeconds()), available);
    }
}

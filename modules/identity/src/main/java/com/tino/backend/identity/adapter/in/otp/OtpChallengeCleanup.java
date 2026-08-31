package com.tino.backend.identity.adapter.in.otp;

import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** Bounded retention for terminal challenges; OTP history is not an audit log. */
public class OtpChallengeCleanup {
    private final OtpChallengeRepository challenges;
    private final Clock clock;

    public OtpChallengeCleanup(OtpChallengeRepository challenges, Clock clock) {
        this.challenges = Objects.requireNonNull(challenges, "challenges");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(fixedDelayString = "${tino.identity.otp.cleanup-interval-ms:3600000}", initialDelay = 3600000)
    @Transactional
    public void purgeTerminalChallenges() {
        challenges.deleteFinishedBefore(Instant.now(clock).minus(Duration.ofDays(2)));
    }
}

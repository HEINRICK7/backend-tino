package com.tino.backend.identity.application.model;

import java.util.Objects;
import java.util.UUID;

public record OtpChallengeStatusView(
        UUID challengeId,
        String status,
        long expiresInSeconds,
        boolean verificationAvailable) {
    public OtpChallengeStatusView {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(status, "status");
    }
}

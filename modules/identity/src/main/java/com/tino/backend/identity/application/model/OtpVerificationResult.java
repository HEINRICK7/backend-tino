package com.tino.backend.identity.application.model;

import java.util.Objects;
import java.util.UUID;

/** One-time identity proof, not an access token and not a tenant credential. */
public record OtpVerificationResult(
        UUID challengeId,
        String verificationStatus,
        String verificationTicket,
        long ticketExpiresInSeconds) {
    public OtpVerificationResult {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(verificationStatus, "verificationStatus");
        Objects.requireNonNull(verificationTicket, "verificationTicket");
    }
}

package com.tino.backend.identity.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Framework-independent, single-use OTP challenge state. */
public record OtpChallenge(
        UUID id,
        PhoneNumber phone,
        String phoneHash,
        String requestOriginHash,
        String codeVerifier,
        OtpChallengeStatus status,
        Instant expiresAt,
        int attemptCount,
        int maxAttempts,
        int resendCount,
        int maxResends,
        Instant resendAvailableAt,
        Instant createdAt,
        Instant verifiedAt,
        Instant consumedAt,
        String verificationTicketHash,
        Instant verificationTicketExpiresAt) {

    public OtpChallenge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(phone, "phone");
        requireText(phoneHash, "phoneHash");
        requireText(codeVerifier, "codeVerifier");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(resendAvailableAt, "resendAvailableAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (attemptCount < 0 || maxAttempts <= 0 || resendCount < 0 || maxResends < 0) {
            throw new IllegalArgumentException("invalid OTP counters");
        }
    }

    public static OtpChallenge pending(
            UUID id,
            PhoneNumber phone,
            String phoneHash,
            String requestOriginHash,
            String codeVerifier,
            Instant expiresAt,
            Instant resendAvailableAt,
            Instant createdAt,
            int maxAttempts,
            int maxResends) {
        return new OtpChallenge(
                id,
                phone,
                phoneHash,
                requestOriginHash,
                codeVerifier,
                OtpChallengeStatus.PENDING,
                expiresAt,
                0,
                maxAttempts,
                0,
                maxResends,
                resendAvailableAt,
                createdAt,
                null,
                null,
                null,
                null);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public OtpChallenge withStatus(OtpChallengeStatus next, Instant verified, Instant consumed) {
        return new OtpChallenge(
                id, phone, phoneHash, requestOriginHash, codeVerifier, next, expiresAt,
                attemptCount, maxAttempts, resendCount, maxResends, resendAvailableAt,
                createdAt, verified, consumed, verificationTicketHash, verificationTicketExpiresAt);
    }

    public OtpChallenge invalidAttempt(Instant now) {
        var nextAttempts = attemptCount + 1;
        var nextStatus = nextAttempts >= maxAttempts ? OtpChallengeStatus.LOCKED : status;
        return new OtpChallenge(
                id, phone, phoneHash, requestOriginHash, codeVerifier, nextStatus, expiresAt,
                nextAttempts, maxAttempts, resendCount, maxResends, resendAvailableAt,
                createdAt, verifiedAt, consumedAt, verificationTicketHash, verificationTicketExpiresAt);
    }

    public OtpChallenge resent(String nextCodeVerifier, Instant nextExpiresAt, Instant nextResendAvailableAt) {
        requireText(nextCodeVerifier, "nextCodeVerifier");
        Objects.requireNonNull(nextExpiresAt, "nextExpiresAt");
        Objects.requireNonNull(nextResendAvailableAt, "nextResendAvailableAt");
        return new OtpChallenge(
                id, phone, phoneHash, requestOriginHash, nextCodeVerifier, OtpChallengeStatus.PENDING,
                nextExpiresAt, 0, maxAttempts, resendCount + 1, maxResends, nextResendAvailableAt,
                createdAt, null, null, null, null);
    }

    public OtpChallenge expired() {
        return withStatus(OtpChallengeStatus.EXPIRED, verifiedAt, consumedAt);
    }

    public OtpChallenge verified(String ticketHash, Instant verifiedAt, Instant ticketExpiresAt) {
        requireText(ticketHash, "ticketHash");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(ticketExpiresAt, "ticketExpiresAt");
        return new OtpChallenge(
                id, phone, phoneHash, requestOriginHash, codeVerifier, OtpChallengeStatus.VERIFIED,
                expiresAt, attemptCount, maxAttempts, resendCount, maxResends, resendAvailableAt,
                createdAt, verifiedAt, null, ticketHash, ticketExpiresAt);
    }

    /** Marks the challenge as verified by a trusted WhatsApp event; ticket issuance is separate. */
    public OtpChallenge whatsappVerified(Instant verifiedAt) {
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        return new OtpChallenge(
                id, phone, phoneHash, requestOriginHash, codeVerifier, OtpChallengeStatus.VERIFIED,
                expiresAt, attemptCount, maxAttempts, resendCount, maxResends, resendAvailableAt,
                createdAt, verifiedAt, null, null, null);
    }

    public OtpChallenge withVerificationTicket(String ticketHash, Instant ticketExpiresAt) {
        requireText(ticketHash, "ticketHash");
        Objects.requireNonNull(ticketExpiresAt, "ticketExpiresAt");
        if (status != OtpChallengeStatus.VERIFIED) {
            throw new IllegalStateException("only a verified challenge can receive a ticket");
        }
        return new OtpChallenge(
                id, phone, phoneHash, requestOriginHash, codeVerifier, status,
                expiresAt, attemptCount, maxAttempts, resendCount, maxResends, resendAvailableAt,
                createdAt, verifiedAt, consumedAt, ticketHash, ticketExpiresAt);
    }

    public OtpChallenge consumed(Instant consumedAt) {
        return withStatus(OtpChallengeStatus.CONSUMED, verifiedAt, consumedAt);
    }

    public OtpChallenge deliveryFailed() {
        return withStatus(OtpChallengeStatus.DELIVERY_FAILED, verifiedAt, consumedAt);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}

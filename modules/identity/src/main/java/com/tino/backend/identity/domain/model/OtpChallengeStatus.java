package com.tino.backend.identity.domain.model;

/** Lifecycle of a pre-authentication OTP challenge. */
public enum OtpChallengeStatus {
    PENDING,
    VERIFIED,
    EXPIRED,
    LOCKED,
    CONSUMED,
    DELIVERY_FAILED,
    CANCELLED;

    /** Maps storage-compatible states to the provider-neutral public lifecycle. */
    public OtpLifecycleStatus canonical() {
        return switch (this) {
            case PENDING -> OtpLifecycleStatus.OTP_SENT;
            case VERIFIED, CONSUMED -> OtpLifecycleStatus.OTP_VERIFIED;
            case EXPIRED -> OtpLifecycleStatus.OTP_EXPIRED;
            case LOCKED -> OtpLifecycleStatus.OTP_RATE_LIMITED;
            case DELIVERY_FAILED -> OtpLifecycleStatus.OTP_FAILED;
            case CANCELLED -> OtpLifecycleStatus.OTP_CANCELLED;
        };
    }
}

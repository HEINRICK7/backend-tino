package com.tino.backend.identity.domain.model;

/** Lifecycle of a pre-authentication OTP challenge. */
public enum OtpChallengeStatus {
    PENDING,
    VERIFIED,
    EXPIRED,
    LOCKED,
    CONSUMED,
    DELIVERY_FAILED
}

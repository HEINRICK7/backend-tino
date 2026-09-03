package com.tino.backend.identity.domain.model;

/** Provider-neutral lifecycle names exposed by the OTP API and telemetry. */
public enum OtpLifecycleStatus {
    OTP_CREATED,
    OTP_SENT,
    OTP_DELIVERED,
    OTP_VERIFIED,
    OTP_EXPIRED,
    OTP_CANCELLED,
    OTP_RATE_LIMITED,
    OTP_FAILED
}

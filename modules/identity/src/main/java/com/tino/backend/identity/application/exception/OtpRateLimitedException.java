package com.tino.backend.identity.application.exception;

/** Safe rate-limit response for OTP request and resend attempts. */
public final class OtpRateLimitedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final long retryAfterSeconds;

    public OtpRateLimitedException(long retryAfterSeconds) {
        super("OTP rate limit reached");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}

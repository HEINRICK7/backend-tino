package com.tino.backend.identity.application.exception;

/** Non-disclosing OTP verification failure with a UI-safe reason. */
public final class OtpVerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    public OtpVerificationException(Reason reason) {
        super("OTP verification failed");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID,
        EXPIRED,
        LOCKED,
        ALREADY_USED
    }
}

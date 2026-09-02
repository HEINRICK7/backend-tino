package com.tino.backend.identity.application.exception;

/** Provider-neutral delivery failure. */
public final class OtpDeliveryException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final boolean retryable;

    public OtpDeliveryException(boolean retryable) {
        super("OTP delivery unavailable");
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}

package com.tino.backend.identity.application.port.out;

/** Port-level translation of an OTP persistence failure. */
public final class OtpPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OtpPersistenceException(Throwable cause) {
        super("OTP persistence failed", cause);
    }
}

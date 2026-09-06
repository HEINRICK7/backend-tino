package com.tino.backend.identity.application.exception;

/** Raised when the internal identity provider cannot be consulted safely. */
public final class OtpAuthorizationUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OtpAuthorizationUnavailableException(Throwable cause) {
        super("OTP authorization lookup unavailable", cause);
    }
}

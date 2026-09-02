package com.tino.backend.identity.application.exception;

/** Raised when an OTP request is structurally invalid. */
public final class OtpInvalidRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OtpInvalidRequestException() {
        super("invalid OTP request");
    }
}

package com.tino.backend.identity.application.exception;

/** A phone-binding update could not be completed safely. */
public final class PhoneIdentityOperationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PhoneIdentityOperationException(Throwable cause) {
        super("phone identity operation failed", cause);
    }
}

package com.tino.backend.identity.application.exception;

/** The requested phone is already bound to another identity. */
public final class PhoneIdentityConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PhoneIdentityConflictException() {
        super("phone is already bound to another identity");
    }
}

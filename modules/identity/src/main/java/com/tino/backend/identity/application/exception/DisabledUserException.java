package com.tino.backend.identity.application.exception;

/** Raised when an otherwise known user is disabled. */
public final class DisabledUserException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DisabledUserException() {
        super("user is disabled");
    }
}

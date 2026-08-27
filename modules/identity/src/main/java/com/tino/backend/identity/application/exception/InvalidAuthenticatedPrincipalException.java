package com.tino.backend.identity.application.exception;

/** Raised when an inbound identity is absent or structurally invalid. */
public final class InvalidAuthenticatedPrincipalException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidAuthenticatedPrincipalException() {
        super("authenticated principal is invalid");
    }

    public InvalidAuthenticatedPrincipalException(Throwable cause) {
        super("authenticated principal is invalid", cause);
    }
}

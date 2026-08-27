package com.tino.backend.business.application.exception;

/** Creation is denied when the authenticated internal User is not active. */
public final class InactiveAuthenticatedUserException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InactiveAuthenticatedUserException() {
        super("authenticated user is inactive");
    }
}

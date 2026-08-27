package com.tino.backend.identity.application.exception;

/** Raised when persistence cannot resolve a user after a create race. */
public final class UserResolutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UserResolutionException() {
        super("authenticated user could not be resolved");
    }

    public UserResolutionException(Throwable cause) {
        super("authenticated user could not be resolved", cause);
    }
}

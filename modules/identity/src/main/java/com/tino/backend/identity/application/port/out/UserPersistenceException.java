package com.tino.backend.identity.application.port.out;

/** Port-level translation of an unexpected user persistence failure. */
public final class UserPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UserPersistenceException(Throwable cause) {
        super("user persistence failed", cause);
    }
}

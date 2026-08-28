package com.tino.backend.bootstrap.application.exception;

/** Safe dependency failure without exposing persistence or provider internals. */
public final class BootstrapContextUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BootstrapContextUnavailableException(Throwable cause) {
        super("bootstrap context is unavailable", cause);
    }
}

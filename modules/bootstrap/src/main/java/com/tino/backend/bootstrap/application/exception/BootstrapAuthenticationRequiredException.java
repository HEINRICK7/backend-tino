package com.tino.backend.bootstrap.application.exception;

/** Defensive failure when the inbound adapter has no usable authenticated principal. */
public final class BootstrapAuthenticationRequiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BootstrapAuthenticationRequiredException() {
        super("authentication is required");
    }
}

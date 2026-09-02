package com.tino.backend.sync.application.exception;

/** Defensive failure for a protected Sync endpoint invoked without identity. */
public final class UnauthenticatedSyncRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UnauthenticatedSyncRequestException() {
        super("authentication is required");
    }
}

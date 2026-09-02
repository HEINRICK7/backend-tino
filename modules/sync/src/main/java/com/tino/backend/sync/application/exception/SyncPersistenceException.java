package com.tino.backend.sync.application.exception;

/** Safe application boundary for persistence failures during Sync Push. */
public final class SyncPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SyncPersistenceException(Throwable cause) {
        super("sync persistence failed", cause);
    }
}

package com.tino.backend.sync.application.exception;

/** Safe public failure for unavailable Sync dependencies. */
public final class SyncUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SyncUnavailableException(Throwable cause) {
        super("sync is unavailable", cause);
    }
}

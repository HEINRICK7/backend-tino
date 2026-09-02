package com.tino.backend.sync.application.exception;

/** Safe denial for a disabled identity at the Sync HTTP boundary. */
public final class SyncAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SyncAccessDeniedException() {
        super("sync access denied");
    }
}

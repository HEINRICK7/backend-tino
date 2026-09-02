package com.tino.backend.sync.application.exception;

/** Pull cannot infer a tenant when the identity can access multiple Businesses. */
public final class SyncBusinessContextRequiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SyncBusinessContextRequiredException() {
        super("business context is required");
    }
}

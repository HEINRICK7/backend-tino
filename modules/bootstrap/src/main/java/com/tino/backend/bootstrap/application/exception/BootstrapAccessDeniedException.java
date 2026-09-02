package com.tino.backend.bootstrap.application.exception;

/** Safe denial for inactive or unauthorized bootstrap context requests. */
public final class BootstrapAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BootstrapAccessDeniedException() {
        super("bootstrap access denied");
    }
}

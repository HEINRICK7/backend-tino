package com.tino.backend.fiscal.application.exception;

/** A tenant-visible fiscal document id does not exist. */
public final class NfeDocumentNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NfeDocumentNotFoundException() {
        super("fiscal document not found");
    }
}

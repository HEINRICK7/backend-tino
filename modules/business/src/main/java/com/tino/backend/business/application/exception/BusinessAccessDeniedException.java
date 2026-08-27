package com.tino.backend.business.application.exception;

/** Generic fail-closed authorization outcome without cross-business disclosure. */
public final class BusinessAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessAccessDeniedException() {
        super("business access denied");
    }
}

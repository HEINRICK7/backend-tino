package com.tino.backend.credit.application.exception;

public final class CreditAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditAccessDeniedException() {
        super("credit access denied");
    }
}

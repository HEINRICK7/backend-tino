package com.tino.backend.credit.application.exception;

public final class CreditConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditConflictException() {
        super("credit idempotency conflict");
    }
}

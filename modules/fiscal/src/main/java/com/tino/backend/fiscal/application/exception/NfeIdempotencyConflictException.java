package com.tino.backend.fiscal.application.exception;

/** Same idempotency key cannot address two different fiscal documents. */
public final class NfeIdempotencyConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NfeIdempotencyConflictException() {
        super("Idempotency-Key was already used for another NF-e");
    }
}

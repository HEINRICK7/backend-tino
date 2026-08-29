package com.tino.backend.customer.application.exception;

public final class CustomerConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CustomerConflictException() {
        super("customer idempotency key was reused with a different request");
    }
}

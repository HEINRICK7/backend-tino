package com.tino.backend.credit.application.exception;

public final class CreditUnauthenticatedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditUnauthenticatedException() {
        super("authentication required");
    }
}

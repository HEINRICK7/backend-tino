package com.tino.backend.credit.application.exception;

public final class CreditAccountNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditAccountNotFoundException() {
        super("credit account not found");
    }
}

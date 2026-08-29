package com.tino.backend.credit.application.exception;

public final class CreditCompensationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditCompensationException(String message) {
        super(message);
    }
}

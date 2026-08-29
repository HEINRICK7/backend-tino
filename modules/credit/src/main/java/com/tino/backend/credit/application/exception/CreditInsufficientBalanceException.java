package com.tino.backend.credit.application.exception;

public final class CreditInsufficientBalanceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditInsufficientBalanceException() {
        super("insufficient credit balance");
    }
}

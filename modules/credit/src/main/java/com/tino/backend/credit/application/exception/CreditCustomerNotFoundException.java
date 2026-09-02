package com.tino.backend.credit.application.exception;

public final class CreditCustomerNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditCustomerNotFoundException() {
        super("customer not found");
    }
}

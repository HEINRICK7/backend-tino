package com.tino.backend.customer.application.exception;

public final class CustomerNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CustomerNotFoundException() {
        super("customer not found");
    }
}

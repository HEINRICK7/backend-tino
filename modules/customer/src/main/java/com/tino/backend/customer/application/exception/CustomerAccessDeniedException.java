package com.tino.backend.customer.application.exception;

public final class CustomerAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CustomerAccessDeniedException() {
        super("customer access denied");
    }
}

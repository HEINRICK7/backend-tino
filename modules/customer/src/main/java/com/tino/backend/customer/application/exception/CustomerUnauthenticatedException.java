package com.tino.backend.customer.application.exception;

public final class CustomerUnauthenticatedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CustomerUnauthenticatedException() {
        super("authentication is required");
    }
}

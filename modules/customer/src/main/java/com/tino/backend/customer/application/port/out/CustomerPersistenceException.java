package com.tino.backend.customer.application.port.out;

public final class CustomerPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CustomerPersistenceException(Throwable cause) {
        super("customer persistence failed", cause);
    }
}

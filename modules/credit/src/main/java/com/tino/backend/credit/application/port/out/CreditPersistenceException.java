package com.tino.backend.credit.application.port.out;

public class CreditPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditPersistenceException(Throwable cause) {
        super("credit persistence failed", cause);
    }
}

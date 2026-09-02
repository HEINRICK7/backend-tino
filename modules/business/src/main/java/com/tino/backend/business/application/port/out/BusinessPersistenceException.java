package com.tino.backend.business.application.port.out;

/** Port-level persistence failure; adapter-specific exceptions do not cross this boundary. */
public class BusinessPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessPersistenceException(Throwable cause) {
        super("business persistence failed", cause);
    }
}

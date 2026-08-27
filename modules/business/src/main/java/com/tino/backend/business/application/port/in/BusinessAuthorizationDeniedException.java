package com.tino.backend.business.application.port.in;

/** Safe public failure returned when Business membership authorization is denied. */
public final class BusinessAuthorizationDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessAuthorizationDeniedException() {
        super("business authorization denied");
    }
}

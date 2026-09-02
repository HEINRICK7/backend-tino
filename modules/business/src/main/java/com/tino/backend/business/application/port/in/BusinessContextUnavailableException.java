package com.tino.backend.business.application.port.in;

/** Safe public failure when Business context composition cannot read its source. */
public final class BusinessContextUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessContextUnavailableException(Throwable cause) {
        super("business context is unavailable", cause);
    }
}

package com.tino.backend.businessunderstanding.application.exception;

public final class BusinessNotReadyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessNotReadyException() {
        super("business understanding is not ready");
    }
}

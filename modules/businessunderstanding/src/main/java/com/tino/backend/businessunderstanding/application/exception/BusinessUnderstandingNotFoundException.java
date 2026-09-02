package com.tino.backend.businessunderstanding.application.exception;

public final class BusinessUnderstandingNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BusinessUnderstandingNotFoundException() {
        super("business understanding item was not found");
    }
}

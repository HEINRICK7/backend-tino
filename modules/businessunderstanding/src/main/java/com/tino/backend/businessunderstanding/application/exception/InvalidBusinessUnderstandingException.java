package com.tino.backend.businessunderstanding.application.exception;

public final class InvalidBusinessUnderstandingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;

    public InvalidBusinessUnderstandingException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

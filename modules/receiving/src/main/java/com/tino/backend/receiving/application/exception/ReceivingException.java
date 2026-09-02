package com.tino.backend.receiving.application.exception;

public final class ReceivingException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ReceivingErrorCode code;
    private final boolean retryable;
    private final int httpStatus;

    public ReceivingException(String message) {
        this(ReceivingErrorCode.INVALID_REQUEST, message, false, 400);
    }

    public ReceivingException(ReceivingErrorCode code, String message) {
        this(code, message, false, 409);
    }

    public ReceivingException(ReceivingErrorCode code, String message, boolean retryable, int httpStatus) {
        super(message);
        this.code = code;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public ReceivingErrorCode code() { return code; }
    public boolean retryable() { return retryable; }
    public int httpStatus() { return httpStatus; }
}

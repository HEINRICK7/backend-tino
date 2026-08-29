package com.tino.backend.sync.application.exception;

/** Expected, non-persistence rejection raised by a registered event handler. */
public final class SyncEventRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final boolean retryable;

    public SyncEventRejectedException(String code, boolean retryable, String message) {
        super(message);
        this.code = requireText(code, "code");
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

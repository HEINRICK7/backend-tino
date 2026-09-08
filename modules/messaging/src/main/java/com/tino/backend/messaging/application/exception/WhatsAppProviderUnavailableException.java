package com.tino.backend.messaging.application.exception;

public final class WhatsAppProviderUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WhatsAppProviderUnavailableException(Throwable cause) {
        super(cause);
    }
}

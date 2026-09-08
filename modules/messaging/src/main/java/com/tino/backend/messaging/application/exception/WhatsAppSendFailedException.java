package com.tino.backend.messaging.application.exception;

public final class WhatsAppSendFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WhatsAppSendFailedException(Throwable cause) {
        super(cause);
    }
}

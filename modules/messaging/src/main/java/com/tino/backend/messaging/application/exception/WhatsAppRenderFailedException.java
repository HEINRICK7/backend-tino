package com.tino.backend.messaging.application.exception;

public final class WhatsAppRenderFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WhatsAppRenderFailedException(Throwable cause) {
        super(cause);
    }
}

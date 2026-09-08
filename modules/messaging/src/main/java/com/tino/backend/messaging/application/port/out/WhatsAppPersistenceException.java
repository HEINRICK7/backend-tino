package com.tino.backend.messaging.application.port.out;

public final class WhatsAppPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WhatsAppPersistenceException(Throwable cause) {
        super(cause);
    }
}

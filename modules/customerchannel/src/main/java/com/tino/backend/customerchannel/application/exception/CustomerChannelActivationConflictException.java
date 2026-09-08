package com.tino.backend.customerchannel.application.exception;

public final class CustomerChannelActivationConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CustomerChannelActivationConflictException() {
        super("customer-channel activation idempotency key was reused with a different request");
    }
}

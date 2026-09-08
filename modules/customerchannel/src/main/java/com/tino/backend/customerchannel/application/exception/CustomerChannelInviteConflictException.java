package com.tino.backend.customerchannel.application.exception;

public final class CustomerChannelInviteConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CustomerChannelInviteConflictException() {
        super("customer-channel invite idempotency key was reused with a different request");
    }
}

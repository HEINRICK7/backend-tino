package com.tino.backend.identity.application.exception;

/** Raised before delivery when a phone is not authorized for the requested business. */
public final class OtpBusinessPhoneNotAuthorizedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OtpBusinessPhoneNotAuthorizedException() {
        super("phone is not authorized for this business");
    }
}

package com.tino.backend.external.application.exception;

public final class ExternalProviderAuthenticationException extends ExternalProviderException {
    private static final long serialVersionUID = 1L;
    public ExternalProviderAuthenticationException() { super("PROVIDER_AUTHENTICATION_FAILED"); }
}

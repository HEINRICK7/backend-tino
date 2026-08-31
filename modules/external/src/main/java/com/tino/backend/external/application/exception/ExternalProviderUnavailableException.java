package com.tino.backend.external.application.exception;

public final class ExternalProviderUnavailableException extends ExternalProviderException {
    private static final long serialVersionUID = 1L;
    public ExternalProviderUnavailableException() { super("PROVIDER_UNAVAILABLE"); }
    public ExternalProviderUnavailableException(Throwable cause) { super("PROVIDER_UNAVAILABLE", cause); }
}

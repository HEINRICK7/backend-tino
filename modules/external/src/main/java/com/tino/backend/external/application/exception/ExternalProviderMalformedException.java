package com.tino.backend.external.application.exception;

public final class ExternalProviderMalformedException extends ExternalProviderException {
    private static final long serialVersionUID = 1L;
    public ExternalProviderMalformedException() { super("PROVIDER_MALFORMED_RESPONSE"); }
    public ExternalProviderMalformedException(Throwable cause) { super("PROVIDER_MALFORMED_RESPONSE", cause); }
}

package com.tino.backend.external.application.exception;

public class ExternalProviderException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String code;

    public ExternalProviderException(String code) { super(code); this.code = code; }
    public ExternalProviderException(String code, Throwable cause) { super(code, cause); this.code = code; }
    public String code() { return code; }
}

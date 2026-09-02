package com.tino.backend.fiscal.adapter.out.serpro;

public final class SerproAuthenticationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SerproAuthenticationException(String message) { super(message); }
    public SerproAuthenticationException(String message, Throwable cause) { super(message, cause); }
}

package com.tino.backend.identity.application.port.out;

/**
 * Port-level translation of the database unique constraint race on
 * {@code users.external_subject}.
 */
public final class ExternalSubjectAlreadyExistsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExternalSubjectAlreadyExistsException(Throwable cause) {
        super("external subject already exists", cause);
    }
}

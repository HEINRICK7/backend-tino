package com.tino.backend.identity.application.port.in;

import com.tino.backend.identity.domain.model.ExternalSubject;
import java.util.Objects;

/** Framework-independent identity contract: only the opaque external subject crosses inward. */
public record AuthenticatedPrincipal(ExternalSubject externalSubject) {
    public AuthenticatedPrincipal {
        Objects.requireNonNull(externalSubject, "externalSubject");
    }

    /** Builds the inbound principal without exposing the identity value object to callers. */
    public static AuthenticatedPrincipal fromSubject(String value) {
        if (value == null || value.isBlank()) {
            throw new com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException();
        }
        return new AuthenticatedPrincipal(new ExternalSubject(value));
    }
}

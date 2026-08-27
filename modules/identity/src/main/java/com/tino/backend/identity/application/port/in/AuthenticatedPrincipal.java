package com.tino.backend.identity.application.port.in;

import com.tino.backend.identity.domain.model.ExternalSubject;
import java.util.Objects;

/** Framework-independent identity contract: only the opaque external subject crosses inward. */
public record AuthenticatedPrincipal(ExternalSubject externalSubject) {
    public AuthenticatedPrincipal {
        Objects.requireNonNull(externalSubject, "externalSubject");
    }
}

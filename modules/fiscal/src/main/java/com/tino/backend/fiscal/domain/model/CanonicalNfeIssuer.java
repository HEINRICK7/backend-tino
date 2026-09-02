package com.tino.backend.fiscal.domain.model;

import java.util.Objects;

public record CanonicalNfeIssuer(String document, String legalName, String tradeName, String stateRegistration) {
    public CanonicalNfeIssuer {
        Objects.requireNonNull(legalName, "issuer legal name");
    }
}

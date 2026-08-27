package com.tino.backend.identity.domain.model;

import java.util.Objects;

/** Opaque, nonblank subject issued by the configured identity provider. */
public record ExternalSubject(String value) {
    public ExternalSubject {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("external subject must not be blank");
        }
    }
}

package com.tino.backend.business.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Opaque internal user identifier used by the membership boundary. */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "value");
    }
}

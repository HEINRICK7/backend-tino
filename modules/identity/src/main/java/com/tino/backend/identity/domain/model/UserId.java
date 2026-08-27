package com.tino.backend.identity.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Opaque identifier for an internal TINO user. */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "value");
    }
}

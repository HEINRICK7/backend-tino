package com.tino.backend.business.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Opaque identifier for a User-to-Business membership. */
public record MembershipId(UUID value) {
    public MembershipId {
        Objects.requireNonNull(value, "value");
    }
}

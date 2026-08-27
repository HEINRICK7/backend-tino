package com.tino.backend.identity.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Minimal global identity record, without tenant, credential, or framework state. */
public record User(
        UserId id,
        ExternalSubject externalSubject,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(externalSubject, "externalSubject");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static User active(
            UserId id, ExternalSubject externalSubject, Instant createdAt, Instant updatedAt) {
        return new User(id, externalSubject, UserStatus.ACTIVE, createdAt, updatedAt);
    }
}

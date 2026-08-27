package com.tino.backend.device.domain.model;

import java.util.Objects;

/**
 * Opaque identifier generated and persisted by the client installation.
 * It is an identity lookup key, never an authorization credential.
 */
public record InstallationExternalId(String value) {
    public static final int MAX_LENGTH = 200;

    public InstallationExternalId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("installation identifier must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("installation identifier is too long");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("installation identifier contains control characters");
        }
    }
}

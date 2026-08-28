package com.tino.backend.bootstrap.application.model;

import java.util.Objects;
import java.util.UUID;

/** Minimal authenticated-user summary; external identity never crosses this boundary. */
public record BootstrapUserSummary(UUID id, String status) {
    public BootstrapUserSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
    }
}

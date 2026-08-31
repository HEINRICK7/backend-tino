package com.tino.backend.bootstrap.application.model;

import java.util.Objects;
import java.util.UUID;

/** Minimal authorized Business summary for the startup experience. */
public record BootstrapBusinessSummary(
        UUID id,
        String tradeName,
        String vertical,
        String status,
        String role,
        String dataSourceType) {
    public BootstrapBusinessSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tradeName, "tradeName");
        Objects.requireNonNull(vertical, "vertical");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(dataSourceType, "dataSourceType");
    }

    public BootstrapBusinessSummary(UUID id, String tradeName, String vertical, String status, String role) {
        this(id, tradeName, vertical, status, role, "TINO_NATIVE");
    }
}

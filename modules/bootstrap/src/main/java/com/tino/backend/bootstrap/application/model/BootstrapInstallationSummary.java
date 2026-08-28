package com.tino.backend.bootstrap.application.model;

import java.util.Objects;
import java.util.UUID;

/** Minimal active-installation summary; no provenance or hardware data is exposed. */
public record BootstrapInstallationSummary(
        UUID id,
        String installationId,
        UUID businessId,
        String status) {
    public BootstrapInstallationSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(status, "status");
    }
}

package com.tino.backend.device.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Minimal active installation view exposed for read-only composition. */
public record ActiveInstallationView(
        UUID installationId,
        String installationExternalId,
        UUID businessId) {
    public ActiveInstallationView {
        Objects.requireNonNull(installationId, "installationId");
        Objects.requireNonNull(installationExternalId, "installationExternalId");
        Objects.requireNonNull(businessId, "businessId");
    }
}

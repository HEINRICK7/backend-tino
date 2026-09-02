package com.tino.backend.business.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Minimal public Business summary for read-only cross-module composition. */
public record AccessibleBusinessView(
        UUID businessId,
        String tradeName,
        String vertical,
        String status,
        String role,
        String dataSourceType) {
    public AccessibleBusinessView {
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(tradeName, "tradeName");
        Objects.requireNonNull(vertical, "vertical");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(dataSourceType, "dataSourceType");
    }

    public AccessibleBusinessView(UUID businessId, String tradeName, String vertical, String status, String role) {
        this(businessId, tradeName, vertical, status, role, "TINO_NATIVE");
    }
}

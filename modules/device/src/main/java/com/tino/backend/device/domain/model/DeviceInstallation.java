package com.tino.backend.device.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tenant-owned logical installation.  The registering User is provenance;
 * Business membership remains the authority for every operation.
 */
public record DeviceInstallation(
        DeviceInstallationId id,
        BusinessId businessId,
        InstallationExternalId externalId,
        InstallationStatus status,
        UUID registeredByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public DeviceInstallation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registeredByUserId, "registeredByUserId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static DeviceInstallation active(
            DeviceInstallationId id,
            BusinessId businessId,
            InstallationExternalId externalId,
            UUID registeredByUserId,
            Instant createdAt,
            Instant updatedAt) {
        return new DeviceInstallation(
                id, businessId, externalId, InstallationStatus.ACTIVE,
                registeredByUserId, createdAt, updatedAt);
    }
}

package com.tino.backend.device.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.device.application.exception.DeviceInstallationAccessDeniedException;
import com.tino.backend.device.application.exception.RevokedDeviceInstallationException;
import com.tino.backend.device.application.port.out.DeviceInstallationRepository;
import com.tino.backend.device.domain.model.DeviceInstallation;
import com.tino.backend.device.domain.model.DeviceInstallationId;
import com.tino.backend.device.domain.model.InstallationExternalId;
import com.tino.backend.device.domain.model.InstallationStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registers an installation only after Business membership authorization and
 * tenant context establishment. PostgreSQL's unique constraint closes the
 * concurrent first-registration race without a process or distributed lock.
 */
public final class RegisterDeviceInstallation {
    private final BusinessAuthorization businessAuthorization;
    private final DeviceInstallationRepository installations;
    private final UuidGenerator ids;
    private final Clock clock;

    public RegisterDeviceInstallation(
            BusinessAuthorization businessAuthorization,
            DeviceInstallationRepository installations,
            UuidGenerator ids,
            Clock clock) {
        this.businessAuthorization = Objects.requireNonNull(businessAuthorization, "businessAuthorization");
        this.installations = Objects.requireNonNull(installations, "installations");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DeviceInstallation execute(
            UUID authenticatedUserId,
            BusinessId requestedBusinessId,
            String installationExternalId) {
        Objects.requireNonNull(installationExternalId, "installationExternalId");
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId");
        Objects.requireNonNull(requestedBusinessId, "requestedBusinessId");
        return businessAuthorization.execute(authenticatedUserId, requestedBusinessId,
                authorizedBusinessId -> registerInAuthorizedTenant(
                        authenticatedUserId,
                        authorizedBusinessId,
                        new InstallationExternalId(installationExternalId)));
    }

    public DeviceInstallation execute(
            UUID authenticatedUserId,
            BusinessId requestedBusinessId,
            InstallationExternalId installationExternalId) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId");
        Objects.requireNonNull(requestedBusinessId, "requestedBusinessId");
        Objects.requireNonNull(installationExternalId, "installationExternalId");
        return businessAuthorization.execute(authenticatedUserId, requestedBusinessId,
                authorizedBusinessId -> registerInAuthorizedTenant(
                        authenticatedUserId, authorizedBusinessId, installationExternalId));
    }

    private DeviceInstallation registerInAuthorizedTenant(
            UUID authenticatedUserId,
            BusinessId authorizedBusinessId,
            InstallationExternalId externalId) {
        var existing = installations.findByExternalId(externalId);
        if (existing.isPresent()) {
            return existingRegistration(existing.orElseThrow(), authorizedBusinessId);
        }

        var now = Instant.now(clock);
        var candidate = DeviceInstallation.active(
                new DeviceInstallationId(ids.next()),
                authorizedBusinessId,
                externalId,
                authenticatedUserId,
                now,
                now);
        if (installations.insertIfAbsent(candidate) == 1) {
            return candidate;
        }

        // A concurrent winner is visible after PostgreSQL resolves the unique
        // conflict. Under RLS, a row owned by another Business remains hidden
        // and is reported only as a safe access denial.
        return installations.findByExternalId(externalId)
                .map(found -> existingRegistration(found, authorizedBusinessId))
                .orElseThrow(DeviceInstallationAccessDeniedException::new);
    }

    private static DeviceInstallation existingRegistration(
            DeviceInstallation existing, BusinessId authorizedBusinessId) {
        if (!existing.businessId().equals(authorizedBusinessId)) {
            throw new DeviceInstallationAccessDeniedException();
        }
        if (existing.status() == InstallationStatus.REVOKED) {
            throw new RevokedDeviceInstallationException();
        }
        if (existing.status() != InstallationStatus.ACTIVE) {
            throw new DeviceInstallationAccessDeniedException();
        }
        return existing;
    }
}

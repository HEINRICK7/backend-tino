package com.tino.backend.device.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.device.application.exception.DeviceInstallationAccessDeniedException;
import com.tino.backend.device.application.exception.RevokedDeviceInstallationException;
import com.tino.backend.device.application.model.ActiveDeviceInstallationContext;
import com.tino.backend.device.application.port.out.DeviceInstallationRepository;
import com.tino.backend.device.domain.model.DeviceInstallation;
import com.tino.backend.device.domain.model.InstallationExternalId;
import com.tino.backend.device.domain.model.InstallationStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

/** Resolves an active installation only inside an already authorized Business. */
public final class ResolveDeviceInstallation {
    private final BusinessAuthorization businessAuthorization;
    private final DeviceInstallationRepository installations;

    public ResolveDeviceInstallation(
            BusinessAuthorization businessAuthorization,
            DeviceInstallationRepository installations) {
        this.businessAuthorization = Objects.requireNonNull(businessAuthorization, "businessAuthorization");
        this.installations = Objects.requireNonNull(installations, "installations");
    }

    public ActiveDeviceInstallationContext execute(
            UUID authenticatedUserId,
            BusinessId requestedBusinessId,
            String installationExternalId) {
        return execute(authenticatedUserId, requestedBusinessId,
                new InstallationExternalId(installationExternalId));
    }

    public ActiveDeviceInstallationContext execute(
            UUID authenticatedUserId,
            BusinessId requestedBusinessId,
            InstallationExternalId installationExternalId) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId");
        Objects.requireNonNull(requestedBusinessId, "requestedBusinessId");
        Objects.requireNonNull(installationExternalId, "installationExternalId");
        return businessAuthorization.execute(authenticatedUserId, requestedBusinessId,
                authorizedBusinessId -> installations.findByExternalId(installationExternalId)
                        .map(found -> resolve(found, authorizedBusinessId))
                        .orElseThrow(DeviceInstallationAccessDeniedException::new));
    }

    private static ActiveDeviceInstallationContext resolve(
            DeviceInstallation installation, BusinessId authorizedBusinessId) {
        if (!installation.businessId().equals(authorizedBusinessId)) {
            throw new DeviceInstallationAccessDeniedException();
        }
        if (installation.status() == InstallationStatus.REVOKED) {
            throw new RevokedDeviceInstallationException();
        }
        if (installation.status() != InstallationStatus.ACTIVE) {
            throw new DeviceInstallationAccessDeniedException();
        }
        return new ActiveDeviceInstallationContext(
                installation.id(), installation.externalId(), installation.businessId());
    }
}
